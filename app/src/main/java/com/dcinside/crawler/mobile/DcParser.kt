package com.dcinside.crawler.mobile

import org.jsoup.Jsoup

object DcParser {

    private val SKIP_ROW_TYPES = setOf(
        "icon_notice",
        "icon_survey",
        "icon_pic",
    )

    fun parseListPage(html: String): List<ArticleSummary> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<ArticleSummary>()
        for (row in doc.select("tr.ub-content")) {
            val dataType = row.attr("data-type").trim()
            if (dataType == "icon_notice") continue

            val numText = row.selectFirst("td.gall_num")?.text()?.trim().orEmpty()
            val postId = parseIntOrNull(numText)?.toLong() ?: continue

            val writer = row.selectFirst("td.gall_writer")
            val uid = writer?.attr("data-uid")?.trim().orEmpty()
            val ip = writer?.attr("data-ip")?.trim().orEmpty()
            if (uid.isBlank() && ip.isBlank()) continue
            val nick = writer?.attr("data-nick")?.trim().orEmpty().ifEmpty {
                writer?.selectFirst("em, b, span")?.text()?.trim().orEmpty().ifEmpty {
                    writer?.text()?.trim().orEmpty()
                }
            }

            val title = row.selectFirst("td.gall_tit a")?.text()?.trim().orEmpty()
            val dateCell = row.selectFirst("td.gall_date")
            val createdAt = dateCell?.attr("title")?.ifBlank { dateCell.text().trim() }.orEmpty()

            val viewText = row.selectFirst("td.gall_count")?.text()?.trim().orEmpty()
            val viewCount = parseIntOrNull(viewText) ?: 0

            val recommendText = row.selectFirst("td.gall_recommend")?.text()?.trim().orEmpty()
            val recommendCount = parseIntOrNull(recommendText) ?: 0

            val replyNumText = row.selectFirst("td.gall_tit .reply_numbox .reply_num")
                ?.text()
                .orEmpty()
            val commentCount = parseIntOrNull(replyNumText) ?: 0

            out += ArticleSummary(
                postId = postId,
                title = title,
                authorUid = uid,
                authorNick = nick,
                authorIp = ip,
                createdAt = createdAt,
                viewCount = viewCount,
                recommendCount = recommendCount,
                commentCount = commentCount,
            )
        }
        return out
    }

    private fun parseIntOrNull(raw: String): Int? {
        if (raw.isEmpty()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return digits.toIntOrNull()
    }
}
