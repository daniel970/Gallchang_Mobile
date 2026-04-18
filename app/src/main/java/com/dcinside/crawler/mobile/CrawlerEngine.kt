package com.dcinside.crawler.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.temporal.ChronoUnit

sealed interface CrawlProgress {
    data class Started(
        val totalPages: Int?,  // null이면 날짜 모드(미정)
    ) : CrawlProgress

    data class PageCompleted(
        val page: Int,
        val totalPages: Int?,
        val articlesOnPage: Int,
        val cumulativeArticles: Int,
        val percent: Int, // 0..100
    ) : CrawlProgress

    data class PageFailed(
        val page: Int,
        val attempt: Int,
        val maxAttempts: Int,
        val error: String,
    ) : CrawlProgress

    data class PageSkipped(
        val page: Int,
        val reason: String,
    ) : CrawlProgress

    data class Finished(
        val ranks: List<UserRank>,
        val totalArticles: Int,
        val totalUsers: Int,
        val rankingMode: RankingMode,
    ) : CrawlProgress

    data object Cancelled : CrawlProgress
}

data class CrawlRequest(
    val boardId: String,
    val range: CrawlRange,
    val rankingMode: RankingMode,
    val delayMillis: Long = DEFAULT_DELAY_MS,
    val maxRetries: Int = 3,
    val concurrency: Int = DEFAULT_CONCURRENCY,
) {
    companion object {
        /** 서버 차단 방지용 기본 대기 시간 (ms) — 워커당 */
        const val DEFAULT_DELAY_MS: Long = 600L

        /** 동시에 받을 페이지 수 */
        const val DEFAULT_CONCURRENCY: Int = 3

        /** 날짜 모드에서 무한 루프를 막기 위한 페이지 상한 */
        const val DATE_MODE_HARD_LIMIT: Int = 5000
    }
}

class CrawlerEngine(
    private val client: DcClient = DcClient(),
) {
    private var job: Job? = null

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start(
        scope: CoroutineScope,
        request: CrawlRequest,
        onProgress: (CrawlProgress) -> Unit,
    ) {
        if (isRunning) return
        job = scope.launch(Dispatchers.IO) {
            runCrawl(request, onProgress)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private suspend fun runCrawl(
        request: CrawlRequest,
        onProgress: (CrawlProgress) -> Unit,
    ) {
        val rateLimiter = RateLimiter(request.delayMillis.coerceAtLeast(0L))
        val userMap = linkedMapOf<String, UserRank>()
        var totalArticles = 0
        val concurrency = request.concurrency.coerceIn(1, 8)
        val semaphore = Semaphore(concurrency)

        val totalPagesKnown: Int? = (request.range as? CrawlRange.Pages)
            ?.let { it.endPage - it.startPage + 1 }
        onProgress(CrawlProgress.Started(totalPages = totalPagesKnown))

        try {
            coroutineScope {
                when (val range = request.range) {
                    is CrawlRange.Pages -> runPagesMode(
                        range = range,
                        request = request,
                        semaphore = semaphore,
                        rateLimiter = rateLimiter,
                        userMap = userMap,
                        onProgress = onProgress,
                        addArticles = { totalArticles += it },
                        getTotalArticles = { totalArticles },
                        totalPagesKnown = totalPagesKnown,
                    )
                    is CrawlRange.Dates -> runDatesMode(
                        range = range,
                        request = request,
                        semaphore = semaphore,
                        rateLimiter = rateLimiter,
                        userMap = userMap,
                        onProgress = onProgress,
                        addArticles = { totalArticles += it },
                        getTotalArticles = { totalArticles },
                    )
                }
            }

            val ranks = userMap.values
                .sortedWith(sorterFor(request.rankingMode))
                .toList()

            onProgress(
                CrawlProgress.Finished(
                    ranks = ranks,
                    totalArticles = totalArticles,
                    totalUsers = ranks.size,
                    rankingMode = request.rankingMode,
                ),
            )
        } catch (_: CancellationException) {
            onProgress(CrawlProgress.Cancelled)
        }
    }

    private suspend fun CoroutineScope.runPagesMode(
        range: CrawlRange.Pages,
        request: CrawlRequest,
        semaphore: Semaphore,
        rateLimiter: RateLimiter,
        userMap: LinkedHashMap<String, UserRank>,
        onProgress: (CrawlProgress) -> Unit,
        addArticles: (Int) -> Unit,
        getTotalArticles: () -> Int,
        totalPagesKnown: Int?,
    ) {
        val total = totalPagesKnown ?: 1
        val pages = (range.startPage..range.endPage).toList()

        // 전체 페이지에 대해 병렬 fetch 요청을 발주하되, semaphore로 동시성을 제한한다.
        val deferreds = pages.map { page ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    fetchWithRetries(
                        boardId = request.boardId,
                        page = page,
                        maxRetries = request.maxRetries,
                        rateLimiter = rateLimiter,
                        onProgress = onProgress,
                    )
                }
            }
        }

        // 결과는 페이지 순서대로 처리
            for ((idx, deferred) in deferreds.withIndex()) {
            currentCoroutineContext().ensureActive()
            val articles = deferred.await() ?: continue
            val kept = accumulate(userMap, articles)
            addArticles(kept)
            val done = idx + 1
            val percent = ((done.toLong() * 100L) / total.toLong()).toInt().coerceIn(0, 100)
            onProgress(
                CrawlProgress.PageCompleted(
                    page = pages[idx],
                    totalPages = totalPagesKnown,
                    articlesOnPage = articles.size,
                    cumulativeArticles = getTotalArticles(),
                    percent = percent,
                ),
            )
        }
    }

    private suspend fun CoroutineScope.runDatesMode(
        range: CrawlRange.Dates,
        request: CrawlRequest,
        semaphore: Semaphore,
        rateLimiter: RateLimiter,
        userMap: LinkedHashMap<String, UserRank>,
        onProgress: (CrawlProgress) -> Unit,
        addArticles: (Int) -> Unit,
        getTotalArticles: () -> Int,
    ) {
        val startDate = range.startDate
        val endDate = range.endDate
        val totalDays = ChronoUnit.DAYS
            .between(startDate, endDate)
            .coerceAtLeast(1L)

        val windowSize = request.concurrency.coerceIn(1, 8) + 1
        val pending = LinkedHashMap<Int, Deferred<List<ArticleSummary>?>>()
        var nextToFetch = 1

        fun enqueue(page: Int) {
            pending[page] = async(Dispatchers.IO) {
                semaphore.withPermit {
                    fetchWithRetries(
                        boardId = request.boardId,
                        page = page,
                        maxRetries = request.maxRetries,
                        rateLimiter = rateLimiter,
                        onProgress = onProgress,
                    )
                }
            }
        }

        // 초기 창 채우기
        repeat(windowSize) {
            if (nextToFetch <= CrawlRequest.DATE_MODE_HARD_LIMIT) {
                enqueue(nextToFetch)
                nextToFetch++
            }
        }

        var current = 1
        var lastPercent = 0
        var shouldStop = false

        while (!shouldStop && current <= CrawlRequest.DATE_MODE_HARD_LIMIT) {
            currentCoroutineContext().ensureActive()
            val deferred = pending.remove(current) ?: break
            val articles = deferred.await()

            if (articles == null) {
                // 실패 페이지 — 다음 페이지 선행 적재 후 진행
                if (nextToFetch <= CrawlRequest.DATE_MODE_HARD_LIMIT) {
                    enqueue(nextToFetch)
                    nextToFetch++
                }
                current++
                continue
            }
            if (articles.isEmpty()) break

            val filtered = articles.mapNotNull { a ->
                val d = ArticleDate.parse(a.createdAt) ?: return@mapNotNull null
                Pair(a, d)
            }

            val inRange = filtered.filter { (_, d) ->
                !d.isBefore(startDate) && !d.isAfter(endDate)
            }.map { it.first }

            val kept = accumulate(userMap, inRange)
            addArticles(kept)

            val lastDate = filtered.lastOrNull()?.second
            val percent = if (lastDate == null) {
                lastPercent
            } else {
                val traversed = ChronoUnit.DAYS
                    .between(lastDate, endDate)
                    .coerceIn(0L, totalDays)
                ((traversed * 100L) / totalDays).toInt().coerceIn(0, 100)
            }
            lastPercent = percent

            onProgress(
                CrawlProgress.PageCompleted(
                    page = current,
                    totalPages = null,
                    articlesOnPage = inRange.size,
                    cumulativeArticles = getTotalArticles(),
                    percent = percent,
                ),
            )

            if (lastDate != null && lastDate.isBefore(startDate)) {
                shouldStop = true
            } else {
                // 한 페이지 처리했으니 창 보충
                if (nextToFetch <= CrawlRequest.DATE_MODE_HARD_LIMIT) {
                    enqueue(nextToFetch)
                    nextToFetch++
                }
            }
            current++
        }

        // 선행 적재된 나머지 fetch는 필요 없으므로 취소
        pending.values.forEach { it.cancel() }
    }

    private suspend fun fetchWithRetries(
        boardId: String,
        page: Int,
        maxRetries: Int,
        rateLimiter: RateLimiter,
        onProgress: (CrawlProgress) -> Unit,
    ): List<ArticleSummary>? {
        for (attempt in 1..maxRetries) {
            rateLimiter.waitBeforeNext()
            val htmlResult = client.fetchListPageHtml(boardId, page)
            if (htmlResult.isSuccess) {
                rateLimiter.onSuccess()
                return DcParser.parseListPage(htmlResult.getOrThrow())
            }
            val msg = htmlResult.exceptionOrNull()?.message ?: "알 수 없는 오류"
            rateLimiter.onFailure()
            onProgress(
                CrawlProgress.PageFailed(
                    page = page,
                    attempt = attempt,
                    maxAttempts = maxRetries,
                    error = msg,
                ),
            )
        }
        onProgress(
            CrawlProgress.PageSkipped(
                page = page,
                reason = "최대 재시도($maxRetries 회) 초과",
            ),
        )
        return null
    }

    private fun sorterFor(mode: RankingMode): Comparator<UserRank> = when (mode) {
        RankingMode.POSTS -> compareByDescending<UserRank> { it.postCount }
            .thenByDescending { it.commentSum }
            .thenByDescending { it.viewSum }
            .thenByDescending { it.recommendSum }

        RankingMode.COMMENTS -> compareByDescending<UserRank> { it.commentSum }
            .thenByDescending { it.postCount }
            .thenByDescending { it.viewSum }
            .thenByDescending { it.recommendSum }
    }

    /**
     * 작성자 정보를 누적한다.
     *
     * **로그인(고정닉) 유저만 집계한다.** DC 목록 HTML 은 `data-uid` 유무로만
     * 로그인 여부를 표시하고, 이른바 "반고정닉"(비로그인 상태에서 비번으로 닉만
     * 고정) 은 유동과 구별되는 플래그가 없어 함께 제외된다.
     *
     * @return 이번 호출에서 실제 집계에 반영된 글의 수
     */
    private fun accumulate(userMap: LinkedHashMap<String, UserRank>, articles: List<ArticleSummary>): Int {
        var added = 0
        for (a in articles) {
            if (a.authorUid.isBlank()) continue  // 유동 IP 제외
            val key = a.authorUid
            added++
            val existing = userMap[key]
            if (existing == null) {
                userMap[key] = UserRank(
                    key = key,
                    uid = a.authorUid,
                    ip = a.authorIp,
                    nick = a.authorNick.ifBlank { key },
                    postCount = 1,
                    commentSum = a.commentCount,
                    viewSum = a.viewCount,
                    recommendSum = a.recommendCount,
                )
            } else {
                existing.postCount += 1
                existing.commentSum += a.commentCount
                existing.viewSum += a.viewCount
                existing.recommendSum += a.recommendCount
                if (existing.nick.isBlank() && a.authorNick.isNotBlank()) {
                    existing.nick = a.authorNick
                }
            }
        }
        return added
    }
}
