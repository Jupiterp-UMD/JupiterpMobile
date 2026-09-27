package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.domain.model.CivilDate
import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.ServedTerm
import kotlin.math.roundToInt

fun Float.toOneDecimalString(): String {
    val rounded = (this * 10).toInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}.0" else "$rounded"
}

/**
 * Escapes a value for use in an iCalendar TEXT property (RFC 5545 §3.3.11):
 * backslash, semicolon, and comma are backslash-escaped; CR/LF become the
 * literal two-character sequence "\n" so a value can never break field or line
 * structure.
 */
internal fun escapeIcsText(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

/**
 * Builds the .ics text for the current selections, or null if the active
 * semester's dates aren't in the lookup tables (callers should surface that
 * instead of exporting events on wrong dates).
 */
fun generateIcsContent(selections: List<ScheduleSelection>): String? {
    val semester = activeSemester() ?: return null
    return generateIcsContent(selections, semester)
}

internal fun generateIcsContent(
    selections: List<ScheduleSelection>,
    semester: SemesterDates
): String {
    val sb = StringBuilder()
    sb.append("BEGIN:VCALENDAR\r\n")
    sb.append("VERSION:2.0\r\n")
    sb.append("PRODID:-//Jupiterp//EN\r\n")
    sb.append("CALSCALE:GREGORIAN\r\n")
    for (selection in selections) {
        val courseCode = selection.course.courseCode
        val sectionCode = selection.section.sectionCode
        val courseName = escapeIcsText(selection.course.name)
        for (meeting in selection.section.meetings) {
            val days: List<DayOfWeek>
            val startTime: Float
            val endTime: Float
            val location: String
            when (meeting) {
                is ClassMeeting.InPerson -> {
                    days = meeting.classtime.daysList
                    startTime = meeting.classtime.start
                    endTime = meeting.classtime.end
                    location = meeting.location.display
                }
                is ClassMeeting.OnlineSync -> {
                    days = meeting.classtime.daysList
                    startTime = meeting.classtime.start
                    endTime = meeting.classtime.end
                    location = "Online Sync"
                }
                else -> continue
            }
            if (days.isEmpty()) continue
            val byDay = days.joinToString(",") { it.toIcsDayCode() }
            // The first meeting on or after the first day of classes. When
            // classes start midweek, an early-week day's first occurrence is
            // the following week, so take the earliest date, not the earliest day.
            val dtStart = days.minOf { icsDateForDay(it, semester.firstClassDayInt) }
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("DTSTART:${dtStart}T${formatIcsTime(startTime)}00\r\n")
            sb.append("DTEND:${dtStart}T${formatIcsTime(endTime)}00\r\n")
            sb.append("RRULE:FREQ=WEEKLY;BYDAY=$byDay;UNTIL=${semester.endIcs}\r\n")
            sb.append("SUMMARY:$courseCode ($sectionCode) - ${escapeIcsText(location)}\r\n")
            sb.append("DESCRIPTION:$courseName\r\n")
            sb.append("END:VEVENT\r\n")
        }
    }
    sb.append("END:VCALENDAR")
    return sb.toString()
}

/**
 * Class dates for one term. [firstClassDayInt] is the first day of classes
 * (YYYYMMDD), which need not be a Monday — Spring 2027 starts on a Wednesday.
 */
internal data class SemesterDates(val firstClassDayInt: Int, val endIcs: String)

// Keyed by term code (see Terms). To add a future semester, add a row.
// Spring 2027 matches the site's term constants (classes Jan 27 – May 11);
// its UNTIL is end-of-day Eastern so a last-day evening class isn't dropped.
private val SEMESTERS = mapOf(
    202508 to SemesterDates(20250825, "20251217T235959Z"),
    202601 to SemesterDates(20260126, "20260520T235959Z"),
    202608 to SemesterDates(20260831, "20261211T235959Z"),
    202701 to SemesterDates(20270127, "20270512T035959Z"),
    202708 to SemesterDates(20270830, "20271217T235959Z"),
    202801 to SemesterDates(20280124, "20280517T235959Z"),
)

/**
 * The dates for the term the planner is showing. Prefers the term the API
 * reports it's serving ([ServedTerm]); without that, guesses from the date:
 * Jan–Mar → Spring of this year, Apr–Oct → Fall, Nov–Dec → next Spring.
 *
 * Returns null when that term's dates aren't in the table above — falling
 * back to another semester would silently export wrong dates.
 */
internal fun activeSemester(
    today: Int = currentDateInt(),
    servedTerm: Int? = ServedTerm.code
): SemesterDates? {
    if (servedTerm != null) return SEMESTERS[servedTerm]
    val year = today / 10000
    val month = (today / 100) % 100
    val term = when {
        month < 4 -> year * 100 + 1
        month < 11 -> year * 100 + 8
        else -> (year + 1) * 100 + 1
    }
    return SEMESTERS[term]
}

/** True when calendar export has valid dates for the current semester. */
fun hasKnownSemesterDates(): Boolean = activeSemester() != null

/**
 * Returns the YYYYMMDD string of the first occurrence of [day] on or after
 * [firstClassDayInt].
 */
internal fun icsDateForDay(day: DayOfWeek, firstClassDayInt: Int): String {
    val offset = (day.column - CivilDate.weekdayColumn(firstClassDayInt) + 7) % 7
    val date = CivilDate.fromEpochDay(CivilDate.toEpochDay(firstClassDayInt) + offset)
    return date.toString()
}

internal fun daysInMonth(year: Int, month: Int) = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 30
}

private fun formatIcsTime(decimal: Float): String {
    // Round to whole minutes; truncating float hours drops a minute for
    // times like X:20 and X:50 (e.g. 19.3333 -> 19:19)
    val totalMinutes = (decimal * 60).roundToInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h.toString().padStart(2, '0')}${m.toString().padStart(2, '0')}"
}

private fun DayOfWeek.toIcsDayCode() = when (this) {
    DayOfWeek.MONDAY -> "MO"
    DayOfWeek.TUESDAY -> "TU"
    DayOfWeek.WEDNESDAY -> "WE"
    DayOfWeek.THURSDAY -> "TH"
    DayOfWeek.FRIDAY -> "FR"
    DayOfWeek.SATURDAY -> "SA"
    DayOfWeek.SUNDAY -> "SU"
}