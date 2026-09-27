package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.data.model.GradeSummaryResponse
import com.jupiterp.jupiterpmobile.data.model.toDistribution
import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.Classtime
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.CourseGrades
import com.jupiterp.jupiterpmobile.domain.model.InstructorCourseGrades
import com.jupiterp.jupiterpmobile.domain.model.Location
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.domain.model.expectedSectionGpa
import com.jupiterp.jupiterpmobile.domain.scheduler.GeneratedSchedule
import com.jupiterp.jupiterpmobile.domain.scheduler.HardConstraints
import com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleGenerator
import com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleMetrics
import com.jupiterp.jupiterpmobile.domain.scheduler.SectionKey
import com.jupiterp.jupiterpmobile.domain.scheduler.SortCriterion
import com.jupiterp.jupiterpmobile.domain.scheduler.sortedByCriterion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Expected GPA in the schedule generator: per section, per schedule, and as a sort. */
class GeneratorGpaTest {

    private fun dist(gpa: Float?, graded: Int) =
        GradeSummaryResponse(graded = graded, a = graded, gpa = gpa).toDistribution()

    private fun section(code: String, slugs: List<String>, meetings: List<ClassMeeting> = emptyList()) = Section(
        courseCode = "CMSC132", sectionCode = code,
        instructors = slugs.map { "Prof $it" }, meetings = meetings,
        openSeats = 10, totalSeats = 30, waitlist = 0, holdfile = null,
        instructorSlugs = slugs
    )

    private val grades = CourseGrades(
        courseCode = "CMSC132",
        course = dist(2.87f, graded = 15000),
        byInstructorSlug = mapOf(
            "veteran" to InstructorCourseGrades("veteran", "Veteran", dist(3.20f, graded = 900)),
            "other" to InstructorCourseGrades("other", "Other", dist(2.60f, graded = 400)),
            // Too few graded students to mean anything
            "newcomer" to InstructorCourseGrades("newcomer", "Newcomer", dist(4.00f, graded = 8))
        )
    )

    // ---- expectedSectionGpa ----

    @Test
    fun usesTheInstructorsRecordInThisCourse() {
        assertEquals(3.20f, expectedSectionGpa(section("0101", listOf("veteran")), grades))
    }

    @Test
    fun thinOrMissingInstructorRecordsFallBackToTheCourse() {
        assertEquals(2.87f, expectedSectionGpa(section("0102", listOf("newcomer")), grades))
        assertEquals(2.87f, expectedSectionGpa(section("0103", listOf("unknown")), grades))
        assertEquals(2.87f, expectedSectionGpa(section("0104", listOf("")), grades))
    }

    @Test
    fun coTaughtSectionsAverageTheirInstructors() {
        val gpa = expectedSectionGpa(section("0105", listOf("veteran", "other")), grades)!!
        assertEquals(2.90f, gpa, 0.001f)
    }

    @Test
    fun noUsableGradeDataMeansNoGpa() {
        assertNull(expectedSectionGpa(section("0101", listOf("veteran")), null))
        val thinCourse = CourseGrades("NEWW100", dist(3.9f, graded = 5), emptyMap())
        assertNull(expectedSectionGpa(section("0101", listOf("x")), thinCourse))
    }

    // ---- Schedule metric ----

    private fun course(code: String, credits: Int, sectionCode: String, startHour: Float) = Course(
        courseCode = code, name = code, minCredits = credits, maxCredits = null,
        description = null, genEds = null, conditions = null,
        sections = listOf(
            Section(
                courseCode = code, sectionCode = sectionCode, instructors = emptyList(),
                meetings = listOf(
                    ClassMeeting.InPerson(Classtime("MWF", startHour, startHour + 0.8f), Location("BLD", "1"))
                ),
                openSeats = 10, totalSeats = 30, waitlist = 0, holdfile = null
            )
        )
    )

    @Test
    fun scheduleGpaIsCreditWeightedAndSkipsSectionsWithoutData() {
        val fourCredit = course("MATH140", credits = 4, sectionCode = "0101", startHour = 9f)
        val oneCredit = course("UNIV100", credits = 1, sectionCode = "0101", startHour = 11f)
        val noData = course("NEWW100", credits = 3, sectionCode = "0101", startHour = 13f)

        val result = ScheduleGenerator.generate(
            courses = listOf(fourCredit, oneCredit, noData),
            constraints = HardConstraints(),
            sectionGpas = mapOf(
                SectionKey("MATH140", "0101") to 2.5f,
                SectionKey("UNIV100", "0101") to 4.0f
            )
        )
        val metrics = result.schedules.single().metrics
        // (2.5 × 4 + 4.0 × 1) / 5 = 2.8, not the unweighted 3.25
        assertEquals(2.8f, metrics.avgGpa!!, 0.001f)
        assertEquals(2, metrics.gpaSectionCount)
        assertEquals(3, metrics.sectionCount)
    }

    @Test
    fun noGradeDataGivesNoScheduleGpa() {
        val result = ScheduleGenerator.generate(
            listOf(course("MATH140", 4, "0101", 9f)), HardConstraints()
        )
        assertNull(result.schedules.single().metrics.avgGpa)
    }

    // ---- Sorting ----

    private fun scheduleWith(gpa: Float?, rating: Float? = null) = GeneratedSchedule(
        selections = emptyList(),
        metrics = ScheduleMetrics(
            avgInstructorRating = rating, ratedSectionCount = 0, sectionCount = 3,
            minCredits = 9, maxCredits = 9, daysWithClasses = 3, totalGapMinutes = 0,
            earliestStartMinutes = 540, latestEndMinutes = 900, minOpenSeats = 1,
            avgGpa = gpa, gpaSectionCount = if (gpa != null) 3 else 0
        )
    )

    @Test
    fun highestGpaSortsDescendingWithUnknownLast() {
        val sorted = listOf(scheduleWith(null), scheduleWith(2.9f), scheduleWith(3.4f))
            .sortedByCriterion(SortCriterion.HIGHEST_GPA)
        assertEquals(listOf(3.4f, 2.9f, null), sorted.map { it.metrics.avgGpa })
    }

    @Test
    fun equalGpasFallBackToRating() {
        val sorted = listOf(scheduleWith(3.0f, rating = 3.1f), scheduleWith(3.0f, rating = 4.5f))
            .sortedByCriterion(SortCriterion.HIGHEST_GPA)
        assertEquals(listOf(4.5f, 3.1f), sorted.map { it.metrics.avgInstructorRating })
    }
}
