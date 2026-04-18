package com.dcinside.crawler.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ArticleDateTest {

    @Test
    fun parse_dashFormat() {
        assertEquals(LocalDate.of(2026, 4, 16), ArticleDate.parse("2026-04-16 14:30:00"))
    }

    @Test
    fun parse_dotFormat() {
        assertEquals(LocalDate.of(2026, 4, 16), ArticleDate.parse("2026.04.16 14:30:00"))
    }

    @Test
    fun parse_slashFormat() {
        assertEquals(LocalDate.of(2026, 4, 16), ArticleDate.parse("2026/04/16"))
    }

    @Test
    fun parse_singleDigitMonthDay() {
        assertEquals(LocalDate.of(2026, 4, 6), ArticleDate.parse("2026-4-6"))
    }

    @Test
    fun parse_shortYearReturnsNull() {
        assertNull(ArticleDate.parse("26-04-16"))
    }

    @Test
    fun parse_timeOnlyReturnsNull() {
        assertNull(ArticleDate.parse("14:30"))
    }

    @Test
    fun parse_blankReturnsNull() {
        assertNull(ArticleDate.parse(""))
        assertNull(ArticleDate.parse(null))
    }
}
