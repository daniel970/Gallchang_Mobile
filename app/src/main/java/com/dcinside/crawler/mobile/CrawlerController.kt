package com.dcinside.crawler.mobile

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 프로세스 스코프에서 크롤링 상태를 유지하는 싱글톤.
 *
 * Activity가 재생성되거나 앱이 백그라운드에 가더라도 크롤 작업 자체는
 * 이 싱글톤의 CoroutineScope 에서 돌고 있으므로 취소되지 않는다.
 * OS 가 프로세스 자체를 죽이지 않도록 하기 위해서는 [CrawlerService] 가
 * 포그라운드 서비스로 함께 올라가 있어야 한다.
 */
object CrawlerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = CrawlerEngine()

    private val _events = MutableSharedFlow<CrawlProgress>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** 진행/완료/실패/취소 이벤트 스트림. */
    val events: SharedFlow<CrawlProgress> = _events.asSharedFlow()

    private val _state = MutableStateFlow(CrawlerSnapshot())
    /** 현재까지의 누적 스냅샷. Activity 재생성 시 UI 복원을 위한 소스. */
    val state: StateFlow<CrawlerSnapshot> = _state.asStateFlow()

    val isRunning: Boolean get() = _state.value.isRunning

    fun start(appContext: Context, request: CrawlRequest, rangeDesc: String) {
        if (_state.value.isRunning) return
        _state.value = CrawlerSnapshot(
            isRunning = true,
            rankingMode = request.rankingMode,
            boardId = request.boardId,
            rangeDesc = rangeDesc,
        )

        // 장시간 작업을 OS 가 죽이지 않도록 포그라운드 서비스 기동
        val intent = Intent(appContext, CrawlerService::class.java)
        ContextCompat.startForegroundService(appContext, intent)

        engine.start(scope, request) { progress ->
            _events.tryEmit(progress)
            _state.update(progress)
            if (progress is CrawlProgress.Finished) {
                saveToHistory(appContext, request, rangeDesc, progress)
            }
        }
    }

    /**
     * 수집 완료 시 자동으로 기록 저장. 저장 실패해도 앱 동작에는 영향 없도록 무시한다.
     */
    private fun saveToHistory(
        appContext: Context,
        request: CrawlRequest,
        rangeDesc: String,
        progress: CrawlProgress.Finished,
    ) {
        scope.launch {
            runCatching {
                val store = CrawlHistoryStore(appContext)
                store.save(
                    CrawlHistoryEntry(
                        id = CrawlHistoryStore.newId(),
                        createdAt = System.currentTimeMillis(),
                        boardId = request.boardId,
                        rangeDesc = rangeDesc,
                        rankingMode = progress.rankingMode,
                        totalArticles = progress.totalArticles,
                        totalUsers = progress.totalUsers,
                        ranks = progress.ranks,
                    ),
                )
            }
        }
    }

    fun cancel() {
        engine.cancel()
    }

    fun reset() {
        _state.value = CrawlerSnapshot()
    }

    private fun MutableStateFlow<CrawlerSnapshot>.update(p: CrawlProgress) {
        value = when (p) {
            is CrawlProgress.Started -> value.copy(
                isRunning = true,
                percent = 0,
                totalPages = p.totalPages,
                lastPage = 0,
                cumulativeArticles = 0,
                lastLog = "",
                ranks = emptyList(),
                totalArticles = 0,
                totalUsers = 0,
                finishedOk = false,
                cancelled = false,
            )
            is CrawlProgress.PageCompleted -> value.copy(
                isRunning = true,
                percent = p.percent,
                totalPages = p.totalPages ?: value.totalPages,
                lastPage = p.page,
                cumulativeArticles = p.cumulativeArticles,
                lastLog = "p${p.page}${p.totalPages?.let { "/$it" } ?: ""} • ${p.percent}% • ${p.cumulativeArticles}글",
            )
            is CrawlProgress.PageFailed -> value.copy(
                lastLog = "p${p.page} 재시도(${p.attempt}/${p.maxAttempts}): ${p.error}",
            )
            is CrawlProgress.PageSkipped -> value.copy(
                lastLog = "p${p.page} 스킵: ${p.reason}",
            )
            is CrawlProgress.Finished -> value.copy(
                isRunning = false,
                percent = 100,
                ranks = p.ranks,
                totalArticles = p.totalArticles,
                totalUsers = p.totalUsers,
                rankingMode = p.rankingMode,
                finishedOk = true,
                cancelled = false,
                lastLog = "완료 • ${p.totalArticles}글 • ${p.totalUsers}명",
            )
            CrawlProgress.Cancelled -> value.copy(
                isRunning = false,
                cancelled = true,
                lastLog = "취소됨",
            )
        }
    }
}

/**
 * UI 복원을 위한 현재 스냅샷.
 * Activity 재생성 시 마지막 상태를 그대로 화면에 복원할 수 있도록 보관한다.
 */
data class CrawlerSnapshot(
    val isRunning: Boolean = false,
    val percent: Int = 0,
    val totalPages: Int? = null,
    val lastPage: Int = 0,
    val cumulativeArticles: Int = 0,
    val lastLog: String = "",
    val rankingMode: RankingMode = RankingMode.POSTS,
    val ranks: List<UserRank> = emptyList(),
    val totalArticles: Int = 0,
    val totalUsers: Int = 0,
    val finishedOk: Boolean = false,
    val cancelled: Boolean = false,
    val boardId: String = "",
    val rangeDesc: String = "",
)
