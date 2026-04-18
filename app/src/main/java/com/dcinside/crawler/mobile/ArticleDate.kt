package com.dcinside.crawler.mobile

import java.time.LocalDate

/**
 * 목록 페이지의 `createdAt` 문자열에서 작성 날짜(LocalDate)를 추출한다.
 *
 * DC 사이트는 `td.gall_date`의 `title` 속성에 `yyyy-MM-dd HH:mm:ss` 형태로,
 * 셀 본문에는 시간만 또는 `MM-dd` 형태로 값이 들어온다.
 * 여러 구분자(`-`, `.`, `/`)와 시간 유무를 허용한다.
 */
object ArticleDate {
    fun parse(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val head = raw.trim()
            .substringBefore(' ')
            .substringBefore('T')
            .replace('.', '-')
            .replace('/', '-')

        // yyyy-MM-dd 형태만 허용. yy-MM-dd, MM-dd 는 연도 판단이 모호해 무시.
        if (!head.matches(Regex("""\d{4}-\d{1,2}-\d{1,2}"""))) return null

        return runCatching {
            val parts = head.split('-')
            LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }.getOrNull()
    }
}
