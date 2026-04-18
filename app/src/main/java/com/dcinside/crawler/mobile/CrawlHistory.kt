package com.dcinside.crawler.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 기록 목록에서 보여줄 요약 정보.
 */
data class CrawlHistoryMeta(
    val id: String,
    val createdAt: Long,
    val boardId: String,
    val rangeDesc: String,
    val rankingMode: RankingMode,
    val totalArticles: Int,
    val totalUsers: Int,
)

/**
 * 기록 상세. 랭킹 전체를 포함한다.
 */
data class CrawlHistoryEntry(
    val id: String,
    val createdAt: Long,
    val boardId: String,
    val rangeDesc: String,
    val rankingMode: RankingMode,
    val totalArticles: Int,
    val totalUsers: Int,
    val ranks: List<UserRank>,
) {
    fun toMeta(): CrawlHistoryMeta = CrawlHistoryMeta(
        id = id,
        createdAt = createdAt,
        boardId = boardId,
        rangeDesc = rangeDesc,
        rankingMode = rankingMode,
        totalArticles = totalArticles,
        totalUsers = totalUsers,
    )
}

/**
 * 앱 내부 저장소에 크롤 기록을 저장/로드/삭제한다.
 *
 * 저장 구조:
 * - `filesDir/history_index.json` : 메타(요약) 배열. 목록 화면에서 빠르게 로드.
 * - `filesDir/history/<id>.json`  : 개별 기록의 상세(랭킹 전체).
 */
class CrawlHistoryStore(context: Context) {

    private val appContext: Context = context.applicationContext
    private val indexFile: File get() = File(appContext.filesDir, INDEX_FILE)
    private val dataDir: File get() = File(appContext.filesDir, DATA_DIR).apply { mkdirs() }

    @Synchronized
    fun listMeta(): List<CrawlHistoryMeta> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            val out = ArrayList<CrawlHistoryMeta>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += CrawlHistoryMeta(
                    id = o.getString("id"),
                    createdAt = o.getLong("createdAt"),
                    boardId = o.getString("boardId"),
                    rangeDesc = o.getString("rangeDesc"),
                    rankingMode = runCatching { RankingMode.valueOf(o.getString("rankingMode")) }
                        .getOrDefault(RankingMode.POSTS),
                    totalArticles = o.optInt("totalArticles", 0),
                    totalUsers = o.optInt("totalUsers", 0),
                )
            }
            out.sortedByDescending { it.createdAt }
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun save(entry: CrawlHistoryEntry): Boolean = runCatching {
        val updated = listMeta().toMutableList().apply {
            removeAll { it.id == entry.id }
            add(entry.toMeta())
        }
        writeIndex(updated)
        File(dataDir, "${entry.id}.json").writeText(entry.toJson().toString())
        true
    }.getOrDefault(false)

    @Synchronized
    fun load(id: String): CrawlHistoryEntry? = runCatching {
        val file = File(dataDir, "$id.json")
        if (!file.exists()) return@runCatching null
        val o = JSONObject(file.readText())
        CrawlHistoryEntry(
            id = o.getString("id"),
            createdAt = o.getLong("createdAt"),
            boardId = o.getString("boardId"),
            rangeDesc = o.getString("rangeDesc"),
            rankingMode = runCatching { RankingMode.valueOf(o.getString("rankingMode")) }
                .getOrDefault(RankingMode.POSTS),
            totalArticles = o.optInt("totalArticles", 0),
            totalUsers = o.optInt("totalUsers", 0),
            ranks = parseRanks(o.optJSONArray("ranks")),
        )
    }.getOrNull()

    @Synchronized
    fun delete(id: String): Boolean = runCatching {
        val remaining = listMeta().filterNot { it.id == id }
        writeIndex(remaining)
        File(dataDir, "$id.json").delete()
        true
    }.getOrDefault(false)

    @Synchronized
    fun deleteAll(): Boolean = runCatching {
        indexFile.delete()
        dataDir.listFiles()?.forEach { it.delete() }
        true
    }.getOrDefault(false)

    private fun writeIndex(metas: List<CrawlHistoryMeta>) {
        val arr = JSONArray()
        for (m in metas.sortedByDescending { it.createdAt }) {
            arr.put(m.toJson())
        }
        indexFile.writeText(arr.toString())
    }

    private fun parseRanks(arr: JSONArray?): List<UserRank> {
        if (arr == null) return emptyList()
        val out = ArrayList<UserRank>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += UserRank(
                key = o.getString("key"),
                uid = o.optString("uid", ""),
                ip = o.optString("ip", ""),
                nick = o.optString("nick", ""),
                postCount = o.optInt("postCount", 0),
                commentSum = o.optInt("commentSum", 0),
                viewSum = o.optInt("viewSum", 0),
                recommendSum = o.optInt("recommendSum", 0),
            )
        }
        return out
    }

    companion object {
        private const val INDEX_FILE = "history_index.json"
        private const val DATA_DIR = "history"

        fun newId(): String = UUID.randomUUID().toString()
    }
}

// ---- JSON 직렬화 헬퍼 ----

private fun CrawlHistoryMeta.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("createdAt", createdAt)
    put("boardId", boardId)
    put("rangeDesc", rangeDesc)
    put("rankingMode", rankingMode.name)
    put("totalArticles", totalArticles)
    put("totalUsers", totalUsers)
}

private fun CrawlHistoryEntry.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("createdAt", createdAt)
    put("boardId", boardId)
    put("rangeDesc", rangeDesc)
    put("rankingMode", rankingMode.name)
    put("totalArticles", totalArticles)
    put("totalUsers", totalUsers)
    val arr = JSONArray()
    for (r in ranks) {
        arr.put(JSONObject().apply {
            put("key", r.key)
            put("uid", r.uid)
            put("ip", r.ip)
            put("nick", r.nick)
            put("postCount", r.postCount)
            put("commentSum", r.commentSum)
            put("viewSum", r.viewSum)
            put("recommendSum", r.recommendSum)
        })
    }
    put("ranks", arr)
}
