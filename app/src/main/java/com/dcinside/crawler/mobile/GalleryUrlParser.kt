package com.dcinside.crawler.mobile

import java.net.URI

object GalleryUrlParser {
    fun parseGalleryUrl(urlOrId: String): String {
        val value = urlOrId.trim()
        if (value.isEmpty()) return value
        if (!value.startsWith("http", ignoreCase = true)) {
            return value
        }

        return try {
            val parsed = URI(value)
            val query = parsed.rawQuery.orEmpty()
            val queryMap = query.split("&")
                .mapNotNull {
                    val kv = it.split("=", limit = 2)
                    if (kv.size != 2) null else kv[0] to kv[1]
                }
                .toMap()

            queryMap["id"]?.takeIf { it.isNotBlank() }?.let { return it }

            val parts = parsed.path.orEmpty().trim('/').split('/')
            val boardIdx = parts.indexOf("board")
            if (boardIdx >= 0 && boardIdx + 1 < parts.size) {
                val candidate = parts[boardIdx + 1]
                if (candidate != "lists") {
                    return candidate
                }
            }
            value
        } catch (_: Exception) {
            value
        }
    }
}
