package com.dcinside.crawler.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryUrlParserTest {

    @Test
    fun parseGalleryUrl_returnsIdQuery() {
        val id = GalleryUrlParser.parseGalleryUrl("https://gall.dcinside.com/mgallery/board/lists/?id=baseball_new11&page=1")
        assertEquals("baseball_new11", id)
    }

    @Test
    fun parseGalleryUrl_returnsPlainId() {
        val id = GalleryUrlParser.parseGalleryUrl("baseball_new11")
        assertEquals("baseball_new11", id)
    }
}
