package com.dcinside.crawler.mobile

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DcClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val galleryTypeCache = ConcurrentHashMap<String, String>()

    fun fetchListPageHtml(boardId: String, page: Int, listNum: Int = DEFAULT_LIST_NUM): Result<String> {
        val gtype = resolveGalleryType(boardId)
            ?: return Result.failure(IllegalStateException("갤러리 타입을 판별할 수 없습니다: $boardId"))
        val url = "https://gall.dcinside.com/$gtype/board/lists/?id=$boardId&page=$page&list_num=$listNum"
        return executeGet(url)
    }

    private fun resolveGalleryType(boardId: String): String? {
        galleryTypeCache[boardId]?.let { return it }
        val candidates = listOf("mgallery", "board", "mini")
        for (type in candidates) {
            val url = "https://gall.dcinside.com/$type/board/lists/?id=$boardId"
            val html = executeGet(url).getOrNull() ?: continue
            val hasRows = runCatching { Jsoup.parse(html).select("tr.ub-content").isNotEmpty() }.getOrDefault(false)
            if (hasRows) {
                galleryTypeCache[boardId] = type
                return type
            }
        }
        return null
    }

    private fun executeGet(url: String): Result<String> {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("HTTP ${resp.code}: ${resp.message}")
                }
                resp.body?.string() ?: error("빈 응답")
            }
        }
    }

    companion object {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

        /** DC 목록 페이지 기본 글 수. 값을 크게 줘서 페이지 수를 줄이면 크롤 속도가 빨라진다. */
        const val DEFAULT_LIST_NUM: Int = 100
    }
}
