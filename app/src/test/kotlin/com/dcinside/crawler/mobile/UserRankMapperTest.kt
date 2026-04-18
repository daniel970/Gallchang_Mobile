package com.dcinside.crawler.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class UserRankMapperTest {

    @Test
    fun postsMode_sortedByPostCount_withSkipRanking() {
        val ranks = listOf(
            rank("u1", "alice", postCount = 10, commentSum = 1),
            rank("u2", "bob", postCount = 10, commentSum = 0),
            rank("u3", "carol", postCount = 5, commentSum = 0),
            rank("u4", "dave", postCount = 1, commentSum = 0),
        )

        val items = UserRankMapper.toItems(ranks, RankingMode.POSTS)

        assertEquals(4, items.size)
        assertEquals(1, items[0].rank)
        assertEquals(1, items[1].rank)
        assertEquals(3, items[2].rank)
        assertEquals(4, items[3].rank)
        assertEquals(10, items[0].primaryValue)
        assertEquals(R.string.fmt_post_count, items[0].primaryLabelRes)
    }

    @Test
    fun postsMode_sharePercentMatchesPostCount() {
        val ranks = listOf(
            rank("u1", "alice", postCount = 8, commentSum = 10),
            rank("u2", "bob", postCount = 2, commentSum = 40),
        )

        val items = UserRankMapper.toItems(ranks, RankingMode.POSTS)

        assertEquals(80.0, items[0].sharePercent, 0.001)
        assertEquals(20.0, items[1].sharePercent, 0.001)
    }

    @Test
    fun commentsMode_primaryIsCommentSum_andShareMatchesCommentSum() {
        val ranks = listOf(
            // alice: 글 많지만 댓글은 적음
            rank("u1", "alice", postCount = 10, commentSum = 10),
            // bob: 글 적지만 댓글 많음
            rank("u2", "bob", postCount = 2, commentSum = 40),
        ).sortedByDescending { it.commentSum } // engine이 정렬한 것 simulate

        val items = UserRankMapper.toItems(ranks, RankingMode.COMMENTS)

        assertEquals(40, items[0].primaryValue)
        assertEquals("bob", items[0].nick)
        assertEquals(R.string.fmt_comment_count, items[0].primaryLabelRes)
        assertEquals(80.0, items[0].sharePercent, 0.001)
        assertEquals(20.0, items[1].sharePercent, 0.001)
    }

    @Test
    fun emptyInput_returnsEmpty() {
        assertEquals(0, UserRankMapper.toItems(emptyList(), RankingMode.POSTS).size)
        assertEquals(0, UserRankMapper.toItems(emptyList(), RankingMode.COMMENTS).size)
    }

    private fun rank(uid: String, nick: String, postCount: Int, commentSum: Int): UserRank {
        return UserRank(
            key = uid,
            uid = uid,
            ip = "",
            nick = nick,
            postCount = postCount,
            commentSum = commentSum,
            viewSum = 0,
            recommendSum = 0,
        )
    }
}
