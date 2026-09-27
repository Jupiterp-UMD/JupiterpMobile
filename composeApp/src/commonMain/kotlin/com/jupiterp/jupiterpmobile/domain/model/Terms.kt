package com.jupiterp.jupiterpmobile.domain.model

import kotlin.concurrent.Volatile

/**
 * UMD term codes: the four-digit year followed by the month the term begins,
 * so Fall 2025 is 202508 and Spring 2026 is 202601. Grade data and reviews
 * only ever use Fall and Spring.
 */
object Terms {

    fun season(code: Int): String? = when (code % 100) {
        1 -> "Spring"
        5 -> "Summer"
        8 -> "Fall"
        12 -> "Winter"
        else -> null
    }

    /** e.g. "Spring 2026"; the raw code when it isn't a recognizable term. */
    fun label(code: Int): String {
        val season = season(code) ?: return code.toString()
        return "$season ${code / 100}"
    }

    /** Compact axis label, e.g. "F25" or "S26". */
    fun shortLabel(code: Int): String {
        val season = season(code) ?: return code.toString()
        return "${season.first()}${(code / 100 % 100).toString().padStart(2, '0')}"
    }

    /** e.g. "Fall 2016 – Spring 2026", or a single label when both ends match. */
    fun rangeLabel(first: Int?, last: Int?): String? = when {
        first == null || last == null -> null
        first == last -> label(first)
        else -> "${label(first)} – ${label(last)}"
    }

    /**
     * Fall and Spring terms that have started as of [today] (YYYYMMDD),
     * newest first. A review is nearly always about the term in progress, so
     * the first entry is what a review form should default to. Mirrors the
     * API's own validation, which rejects anything but Fall and Spring.
     */
    fun startedTerms(today: Int, count: Int = 12): List<Int> {
        val year = today / 10000
        val month = (today / 100) % 100
        var term = year * 100 + if (month >= 8) 8 else 1
        return List(count) {
            val current = term
            term = if (term % 100 == 8) term - 7 else (term / 100 - 1) * 100 + 8
            current
        }
    }
}

/**
 * The term whose sections the API is currently serving. The scraper moves to
 * the next term when registration opens (Spring 2027 went live in September
 * 2026), well before that term starts, so the date alone can't say which term
 * the planner is showing. Set once the app has asked the API.
 */
object ServedTerm {
    @Volatile
    var code: Int? = null
}

/**
 * Minimal civil-date arithmetic on YYYYMMDD integers, enough for calendar
 * export without pulling in a date library.
 */
internal object CivilDate {

    /** Days since 1970-01-01 (Howard Hinnant's days_from_civil). */
    fun toEpochDay(ymd: Int): Long {
        var y = (ymd / 10000).toLong()
        val m = ((ymd / 100) % 100).toLong()
        val d = (ymd % 100).toLong()
        if (m <= 2) y -= 1
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    fun fromEpochDay(epochDay: Long): Int {
        val z = epochDay + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return (y * 10000 + m * 100 + d).toInt()
    }

    /** 0 = Monday … 6 = Sunday, matching [DayOfWeek.column]. */
    fun weekdayColumn(ymd: Int): Int {
        // 1970-01-01 was a Thursday (column 3)
        return (((toEpochDay(ymd) + 3) % 7 + 7) % 7).toInt()
    }
}
