package com.dcinside.crawler.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class DcParserTest {

    @Test
    fun parseListPage_skipsNoticeAndInvalidRows() {
        val html = """
            <table>
              <tr class="ub-content" data-type="icon_notice">
                <td class="gall_num">공지</td>
              </tr>
              <tr class="ub-content">
                <td class="gall_num">98765</td>
                <td class="gall_tit">
                  <a>테스트 글 제목</a>
                  <span class="reply_numbox"><span class="reply_num">[3]</span></span>
                </td>
                <td class="gall_writer" data-uid="user01" data-nick="테스터" data-ip=""></td>
                <td class="gall_date" title="2026.04.16 14:30:00">14:30</td>
                <td class="gall_count">150</td>
                <td class="gall_recommend">5</td>
              </tr>
              <tr class="ub-content">
                <td class="gall_num">설문</td>
              </tr>
            </table>
        """.trimIndent()

        val articles = DcParser.parseListPage(html)
        assertEquals(1, articles.size)
        val a = articles[0]
        assertEquals(98765L, a.postId)
        assertEquals("테스트 글 제목", a.title)
        assertEquals("user01", a.authorUid)
        assertEquals("테스터", a.authorNick)
        assertEquals("2026.04.16 14:30:00", a.createdAt)
        assertEquals(150, a.viewCount)
        assertEquals(5, a.recommendCount)
        assertEquals(3, a.commentCount)
    }

    @Test
    fun parseListPage_allowsIpWhenUidMissing() {
        val html = """
            <table>
              <tr class="ub-content">
                <td class="gall_num">11</td>
                <td class="gall_tit"><a>유동 글</a></td>
                <td class="gall_writer" data-uid="" data-nick="유동" data-ip="1.2.3.4"></td>
                <td class="gall_date" title="2026.04.16 15:00:00">15:00</td>
                <td class="gall_count">1</td>
                <td class="gall_recommend">0</td>
              </tr>
            </table>
        """.trimIndent()

        val articles = DcParser.parseListPage(html)
        assertEquals(1, articles.size)
        assertEquals("", articles[0].authorUid)
        assertEquals("1.2.3.4", articles[0].authorIp)
    }
}
