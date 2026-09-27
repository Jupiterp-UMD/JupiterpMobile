package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.data.model.GradeSummaryResponse
import com.jupiterp.jupiterpmobile.data.model.toDistribution
import com.jupiterp.jupiterpmobile.domain.model.CivilDate
import com.jupiterp.jupiterpmobile.domain.model.GradeBucket
import com.jupiterp.jupiterpmobile.domain.model.Terms
import com.jupiterp.jupiterpmobile.ui.components.toGpaString
import com.jupiterp.jupiterpmobile.ui.components.withThousands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradesAndTermsTest {

    // CMSC132 course-wide row, as /v1/grades/summary returned it
    private val cmsc132 = GradeSummaryResponse(
        courseCode = "CMSC132", sectionCount = 534, termCount = 31, firstTerm = 201008, lastTerm = 202508,
        total = 16898, graded = 14989,
        aPlus = 954, a = 2582, aMinus = 1648, bPlus = 1394, b = 2484, bMinus = 1200,
        cPlus = 949, c = 1442, cMinus = 825, dPlus = 97, d = 774, dMinus = 116,
        f = 524, w = 1076, other = 574, gpa = 2.868f
    )

    // ---- Grade distributions ----

    @Test
    fun bucketsSumTheirLetters() {
        val dist = cmsc132.toDistribution()
        assertEquals(954 + 2582 + 1648, dist.buckets[GradeBucket.A])
        assertEquals(524, dist.buckets[GradeBucket.F])
        assertEquals(1076, dist.buckets[GradeBucket.W])
    }

    @Test
    fun barDenominatorIsGradedPlusWithdrawalsNotTotal() {
        val dist = cmsc132.toDistribution()
        assertEquals(14989 + 1076, dist.barTotal)
        val sum = GradeBucket.entries.sumOf { dist.share(it).toDouble() }
        assertEquals(1.0, sum, 0.0001)
    }

    @Test
    fun gpaComesFromTheApiUnchanged() {
        assertEquals(2.868f, cmsc132.toDistribution().gpa)
    }

    @Test
    fun tinyNonzeroShareShowsLessThanOne() {
        val dist = GradeSummaryResponse(graded = 400, a = 399, f = 1, gpa = 3.99f).toDistribution()
        assertEquals("<1", dist.percentLabel(GradeBucket.F))
        assertEquals("0", dist.percentLabel(GradeBucket.D))
    }

    @Test
    fun fewGradedStudentsMeansNoGpa() {
        val thin = GradeSummaryResponse(graded = 19, a = 19, gpa = 4.0f).toDistribution()
        assertFalse(thin.hasEnoughForGpa)
        val enough = GradeSummaryResponse(graded = 20, a = 20, gpa = 4.0f).toDistribution()
        assertTrue(enough.hasEnoughForGpa)
        val nobodyGraded = GradeSummaryResponse(graded = 0, w = 30, gpa = null).toDistribution()
        assertFalse(nobodyGraded.hasEnoughForGpa)
    }

    @Test
    fun perTermRowCoversExactlyItsTerm() {
        val dist = GradeSummaryResponse(term = 202508, graded = 431, gpa = 2.967f).toDistribution()
        assertEquals(202508, dist.firstTerm)
        assertEquals(202508, dist.lastTerm)
        assertEquals("Fall 2025", dist.termRange)
    }

    @Test
    fun courseRowShowsItsRange() {
        assertEquals("Fall 2010 – Fall 2025", cmsc132.toDistribution().termRange)
    }

    // ---- Formatting ----

    @Test
    fun gpaFormatsToTwoDecimals() {
        assertEquals("2.87", 2.868f.toGpaString())
        assertEquals("3.00", 3f.toGpaString())
        assertEquals("3.05", 3.05f.toGpaString())
    }

    @Test
    fun thousandsSeparators() {
        assertEquals("14,989", 14989.withThousands())
        assertEquals("999", 999.withThousands())
        assertEquals("1,000,000", 1_000_000.withThousands())
    }

    // ---- Terms ----

    @Test
    fun termLabels() {
        assertEquals("Spring 2027", Terms.label(202701))
        assertEquals("Fall 2025", Terms.label(202508))
        assertEquals("F25", Terms.shortLabel(202508))
        assertEquals("S07", Terms.shortLabel(200701))
        assertEquals("123", Terms.label(123))
    }

    @Test
    fun rangeCollapsesWhenBothEndsMatch() {
        assertEquals("Spring 2026", Terms.rangeLabel(202601, 202601))
        assertNull(Terms.rangeLabel(null, 202601))
    }

    @Test
    fun startedTermsDuringFallLeadWithFall() {
        assertEquals(listOf(202608, 202601, 202508, 202501), Terms.startedTerms(20260926, count = 4))
    }

    @Test
    fun startedTermsBeforeAugustLeadWithSpring() {
        // Spring 2027 hasn't started in July 2026; the latest started term is Spring 2026
        assertEquals(listOf(202601, 202508, 202501), Terms.startedTerms(20260715, count = 3))
        assertEquals(202701, Terms.startedTerms(20270105, count = 1).single())
    }

    // ---- Civil dates ----

    @Test
    fun weekdayOfKnownDates() {
        assertEquals(2, CivilDate.weekdayColumn(20270127)) // Wednesday
        assertEquals(0, CivilDate.weekdayColumn(20260831)) // Monday
        assertEquals(3, CivilDate.weekdayColumn(19700101)) // Thursday
        assertEquals(1, CivilDate.weekdayColumn(20240227)) // Tuesday
    }

    @Test
    fun epochDayRoundTripsAcrossLeapDaysAndYearEnds() {
        listOf(20240229, 20241231, 20250101, 20000229, 21000301).forEach { date ->
            assertEquals(date, CivilDate.fromEpochDay(CivilDate.toEpochDay(date)))
        }
        assertEquals(20240301, CivilDate.fromEpochDay(CivilDate.toEpochDay(20240229) + 1))
    }
}
