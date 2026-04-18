package com.dcinside.crawler.mobile

import java.time.LocalDate

data class ArticleSummary(
    val postId: Long,
    val title: String,
    val authorUid: String,
    val authorNick: String,
    val authorIp: String,
    val createdAt: String,
    val viewCount: Int,
    val recommendCount: Int,
    val commentCount: Int,
)

data class UserRank(
    val key: String, // uid 우선, 없으면 ip
    val uid: String,
    val ip: String,
    var nick: String,
    var postCount: Int = 0,
    var commentSum: Int = 0,
    var viewSum: Int = 0,
    var recommendSum: Int = 0,
)

/** 수집 기준: 페이지 범위 또는 날짜 범위 */
sealed interface CrawlRange {
    data class Pages(val startPage: Int, val endPage: Int) : CrawlRange
    data class Dates(val startDate: LocalDate, val endDate: LocalDate) : CrawlRange
}

/** 랭킹 정렬 기준 */
enum class RankingMode {
    POSTS,     // 작성글 수 기준
    COMMENTS,  // (작성한 글에 달린) 댓글 수 합 기준
}
