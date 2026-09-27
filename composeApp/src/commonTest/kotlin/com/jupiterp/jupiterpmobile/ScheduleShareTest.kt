package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.deeplink.AppLinks
import com.jupiterp.jupiterpmobile.deeplink.CourseSectionPair
import com.jupiterp.jupiterpmobile.deeplink.ShareLink
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Outgoing share links: built by the app, opened by the site and by the app. */
class ScheduleShareTest {

    private fun selection(courseCode: String, sectionCode: String, colorIndex: Int = 0) = ScheduleSelection(
        course = Course(
            courseCode = courseCode, name = "$courseCode Name", minCredits = 3, maxCredits = null,
            description = null, genEds = null, conditions = null, sections = null
        ),
        section = Section(
            courseCode = courseCode, sectionCode = sectionCode, instructors = emptyList(),
            meetings = emptyList(), openSeats = 0, totalSeats = 0, waitlist = 0, holdfile = null
        ),
        colorIndex = colorIndex
    )

    @Test
    fun linkDecodesBackToTheSameSections() {
        val share = assertNotNull(
            AppLinks.scheduleShare(listOf(selection("CMSC131", "0101"), selection("MATH140", "0501")))
        )
        assertTrue(share.url.startsWith("https://jupiterp.com/?s="), share.url)
        val token = assertNotNull(ShareLink.extractShareToken(share.url))
        assertEquals(
            listOf(CourseSectionPair("CMSC131", "0101"), CourseSectionPair("MATH140", "0501")),
            ShareLink.decodeSchedule(token)
        )
        assertEquals(emptyList(), share.skippedCourses)
    }

    @Test
    fun coursesWithoutASectionAreLeftOutAndReported() {
        val share = assertNotNull(
            AppLinks.scheduleShare(listOf(selection("CMSC131", "0101"), selection("ENGL101", "---")))
        )
        assertEquals(listOf("ENGL101"), share.skippedCourses)
        assertEquals(
            listOf(CourseSectionPair("CMSC131", "0101")),
            ShareLink.decodeSchedule(ShareLink.extractShareToken(share.url)!!)
        )
    }

    @Test
    fun nothingToShareWhenNoCourseHasASection() {
        assertNull(AppLinks.scheduleShare(listOf(selection("ENGL101", "---"))))
        assertNull(AppLinks.scheduleShare(emptyList()))
    }

    @Test
    fun shareLinksAreNotMistakenForReviewOrProfessorLinks() {
        // The app's own share link must fall through to the schedule import
        val share = assertNotNull(AppLinks.scheduleShare(listOf(selection("CMSC131", "0101"))))
        assertNull(AppLinks.parse(share.url))
    }
}
