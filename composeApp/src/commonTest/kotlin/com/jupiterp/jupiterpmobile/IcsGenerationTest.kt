package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.Classtime
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.Location
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Characterization tests for ICS calendar export. Expected values are derived
 * from the SEMESTERS table in Util.kt and pin the current correct behavior.
 */
class IcsGenerationTest {

    private fun course(name: String, code: String = "CMSC131") = Course(
        courseCode = code,
        name = name,
        minCredits = 3,
        maxCredits = null,
        description = null,
        genEds = null,
        conditions = null,
        sections = null
    )

    private fun section(code: String, meetings: List<ClassMeeting>) = Section(
        courseCode = "CMSC131",
        sectionCode = code,
        instructors = emptyList(),
        meetings = meetings,
        openSeats = 10,
        totalSeats = 30,
        waitlist = 0,
        holdfile = null
    )

    private fun selection(
        name: String,
        sectionCode: String,
        meetings: List<ClassMeeting>,
        code: String = "CMSC131"
    ) = ScheduleSelection(
        course = course(name, code),
        section = section(sectionCode, meetings),
        colorIndex = 0
    )

    // ---- Semester selection (activeSemester(today)) ----

    @Test
    fun januarySelectsSpringOfSameYear() {
        val semester = activeSemester(20260115)
        assertNotNull(semester)
        assertEquals(20260126, semester.firstClassDayInt)
    }

    @Test
    fun aprilSelectsFallOfSameYearLowerBoundary() {
        // April is < 11 -> Fall of this year (the lower month boundary).
        assertEquals(20260831, activeSemester(20260401, servedTerm = null)?.firstClassDayInt)
    }

    @Test
    fun novemberSelectsSpringOfNextYearUpperBoundary() {
        // November -> Spring of next year (the upper boundary). Spring 2027
        // classes start on Wednesday, Jan 27.
        assertEquals(20270127, activeSemester(20261101, servedTerm = null)?.firstClassDayInt)
    }

    @Test
    fun decemberSelectsSpringOfNextYear() {
        assertEquals(20270127, activeSemester(20261215, servedTerm = null)?.firstClassDayInt)
    }

    @Test
    fun yearAbsentFromTablesReturnsNull() {
        assertNull(activeSemester(20990101, servedTerm = null))
    }

    @Test
    fun servedTermWinsOverTheDate() {
        // In September the API already serves Spring 2027 sections, so the
        // export must use Spring dates, not Fall's
        assertEquals(20270127, activeSemester(20260926, servedTerm = 202701)?.firstClassDayInt)
    }

    @Test
    fun servedTermMissingFromTableReturnsNull() {
        assertNull(activeSemester(20260926, servedTerm = 209901))
    }

    @Test
    fun midweekStartPushesEarlierDaysToTheFollowingWeek() {
        // Spring 2027 starts Wednesday Jan 27: Wednesday meets that day,
        // Monday's first class is Feb 1, Friday's is Jan 29
        assertEquals("20270127", icsDateForDay(DayOfWeek.WEDNESDAY, 20270127))
        assertEquals("20270201", icsDateForDay(DayOfWeek.MONDAY, 20270127))
        assertEquals("20270129", icsDateForDay(DayOfWeek.FRIDAY, 20270127))
    }

    @Test
    fun mwfClassStartsOnTheFirstClassDayNotTheFirstMonday() {
        val ics = generateIcsContent(
            listOf(
                selection(
                    "Calc",
                    "0101",
                    listOf(ClassMeeting.InPerson(Classtime("MWF", 9f, 9.8333f), Location("MTH", "0304")))
                )
            ),
            SemesterDates(20270127, "20270512T035959Z")
        )
        assertTrue(ics.contains("DTSTART:20270127T090000"))
        assertFalse(ics.contains("DTSTART:20270125"))
    }

    // ---- Date math (icsDateForDay) ----

    @Test
    fun mondayHasNoOffset() {
        assertEquals("20260831", icsDateForDay(DayOfWeek.MONDAY, 20260831))
    }

    @Test
    fun tuesdayWrapsToNextMonth() {
        // Aug 31 + 1 day -> Sep 1.
        assertEquals("20260901", icsDateForDay(DayOfWeek.TUESDAY, 20260831))
    }

    @Test
    fun sundayWrapsToNextMonth() {
        // Aug 31 + 6 days -> Sep 6.
        assertEquals("20260906", icsDateForDay(DayOfWeek.SUNDAY, 20260831))
    }

    @Test
    fun fridayWithinMonthNoWrap() {
        assertEquals("20260130", icsDateForDay(DayOfWeek.FRIDAY, 20260126))
    }

    @Test
    fun fridayWrapsAcrossYearBoundary() {
        // Monday Dec 29, 2025 + 4 days -> Friday Jan 2 of the next year.
        assertEquals("20260102", icsDateForDay(DayOfWeek.FRIDAY, 20251229))
    }

    // ---- Full ICS document (generateIcsContent(selections, semester)) ----

    @Test
    fun fullDocumentHasExpectedEventStructure() {
        val semester = SemesterDates(20260831, "20261211T235959Z")
        val sel = selection(
            name = "Intro to CS",
            sectionCode = "0101",
            meetings = listOf(
                ClassMeeting.InPerson(
                    Classtime("TuTh", 11f, 12.25f),
                    Location("CSI", "1115")
                )
            )
        )

        val ics = generateIcsContent(listOf(sel), semester)

        assertTrue(ics.contains("BEGIN:VEVENT"), ics)
        assertTrue(ics.contains("END:VEVENT"), ics)
        // First day = Tuesday = Aug 31 + 1 = Sep 1.
        assertTrue(ics.contains("DTSTART:20260901T110000"), ics)
        assertTrue(ics.contains("DTEND:20260901T121500"), ics)
        assertTrue(
            ics.contains("RRULE:FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20261211T235959Z"),
            ics
        )
        assertTrue(ics.contains("SUMMARY:") && ics.contains("CSI 1115"), ics)
        assertTrue(ics.contains("DESCRIPTION:Intro to CS"), ics)
    }

    // ---- Time rounding through the document ----

    @Test
    fun timesRoundToWholeMinutesNotTruncated() {
        val semester = SemesterDates(20260831, "20261211T235959Z")
        val sel = selection(
            name = "Evening Seminar",
            sectionCode = "0101",
            meetings = listOf(
                ClassMeeting.InPerson(
                    // 7:20 PM - 7:50 PM stored as float hours.
                    Classtime("M", 19f + 20f / 60f, 19f + 50f / 60f),
                    Location("CSI", "1115")
                )
            )
        )

        val ics = generateIcsContent(listOf(sel), semester)

        assertTrue(ics.contains("T192000"), ics)
        assertTrue(ics.contains("T195000"), ics)
        // Not truncated to 19/49.
        assertFalse(ics.contains("T191900"), ics)
        assertFalse(ics.contains("T194900"), ics)
    }

    // ---- Escaping ----

    @Test
    fun commaInLocationAndNameIsEscaped() {
        val semester = SemesterDates(20260831, "20261211T235959Z")
        val sel = selection(
            name = "Algo, Intro",
            sectionCode = "0101",
            meetings = listOf(
                ClassMeeting.InPerson(
                    Classtime("M", 10f, 11f),
                    // display = "CSI, Annex 1115"
                    Location("CSI, Annex", "1115")
                )
            )
        )

        val ics = generateIcsContent(listOf(sel), semester)

        assertTrue(ics.contains("CSI\\, Annex 1115"), ics)
        assertTrue(ics.contains("DESCRIPTION:Algo\\, Intro"), ics)
        // The raw comma must not survive in those fields.
        assertFalse(ics.contains("DESCRIPTION:Algo, Intro"), ics)
    }

    @Test
    fun newlineInNameDoesNotBreakLine() {
        val semester = SemesterDates(20260831, "20261211T235959Z")
        val sel = selection(
            name = "Line1\nLine2",
            sectionCode = "0101",
            meetings = listOf(
                ClassMeeting.InPerson(
                    Classtime("M", 10f, 11f),
                    Location("CSI", "1115")
                )
            )
        )

        val ics = generateIcsContent(listOf(sel), semester)

        // Newline became the literal two-character escape.
        assertTrue(ics.contains("Line1\\nLine2"), ics)
        // The newline did not break the DESCRIPTION line.
        assertFalse(ics.contains("DESCRIPTION:Line1\nLine2"), ics)
    }

    @Test
    fun nameWithoutSpecialCharsIsUnchanged() {
        val semester = SemesterDates(20260831, "20261211T235959Z")
        val sel = selection(
            name = "Plain Title",
            sectionCode = "0101",
            meetings = listOf(
                ClassMeeting.InPerson(
                    Classtime("M", 10f, 11f),
                    Location("CSI", "1115")
                )
            )
        )

        val ics = generateIcsContent(listOf(sel), semester)

        assertTrue(ics.contains("DESCRIPTION:Plain Title"), ics)
    }
}
