package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.data.api.parseContentRangeTotal
import com.jupiterp.jupiterpmobile.data.model.CourseResponse
import com.jupiterp.jupiterpmobile.data.model.InstructorResponse
import com.jupiterp.jupiterpmobile.data.model.SectionResponse
import com.jupiterp.jupiterpmobile.data.model.SubmitReviewRequest
import com.jupiterp.jupiterpmobile.data.model.toDomain
import com.jupiterp.jupiterpmobile.data.storage.AppData
import com.jupiterp.jupiterpmobile.deeplink.AppLink
import com.jupiterp.jupiterpmobile.deeplink.AppLinks
import com.jupiterp.jupiterpmobile.domain.model.Instructor
import com.jupiterp.jupiterpmobile.domain.model.InstructorDirectory
import com.jupiterp.jupiterpmobile.domain.model.ReviewDraft
import com.jupiterp.jupiterpmobile.domain.model.ReviewRules
import com.jupiterp.jupiterpmobile.domain.model.ReviewStatus
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.domain.model.formatIsoMonthYear
import com.jupiterp.jupiterpmobile.domain.model.normalizeNameForSearch
import com.jupiterp.jupiterpmobile.domain.model.summarizeCourseInstructors
import com.jupiterp.jupiterpmobile.ui.components.toHalfStepString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewsAndProfessorsTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    // ---- API contract parsing ----

    @Test
    fun parsesAV1InstructorRowIncludingFieldsTheAppIgnores() {
        val body = """[{"slug":"anwar-mamat","name":"Anwar Mamat","average_rating":3.66,"id":222,
            "name_norm":"anwar mamat","pt_slug":"mamat","pt_average_rating":3.66,"pt_review_count":83,
            "pt_snapshot_at":"2026-09-13T03:03:48+00:00","jupiterp_rating":null,"jupiterp_review_count":0,
            "combined_rating":3.66,"first_seen_term":202508,"last_seen_term":202701,"is_active":true}]"""
        val instructor = json.decodeFromString(ListSerializer(InstructorResponse.serializer()), body).single().toDomain()
        assertEquals("anwar-mamat", instructor.slug)
        assertEquals(3.66f, instructor.rating)
        assertEquals(83, instructor.ptReviewCount)
        assertEquals(202701, instructor.lastSeenTerm)
        assertTrue(instructor.isActive)
    }

    @Test
    fun trimmedColumnsStillParse() {
        val body = """[{"slug":"a-seyed","name":"A Seyed","average_rating":null,"combined_rating":null}]"""
        val instructor = json.decodeFromString(ListSerializer(InstructorResponse.serializer()), body).single().toDomain()
        assertNull(instructor.rating)
        assertEquals(0, instructor.jupiterpReviewCount)
    }

    @Test
    fun servedTermLookupColumnsDecode() {
        // getServedTerm asks for only these columns; the model needs slug and name to decode
        val body = """[{"slug":"kemi-busari","name":"Kemi Busari","last_seen_term":202701}]"""
        val row = json.decodeFromString(ListSerializer(InstructorResponse.serializer()), body).single()
        assertEquals(202701, row.lastSeenTerm)
    }

    @Test
    fun combinedRatingWinsOverTheV0Field() {
        val instructor = Instructor(name = "X", slug = "x", averageRating = 3.0f, combinedRating = 4.2f)
        assertEquals(4.2f, instructor.rating)
    }

    @Test
    fun sectionSlugsAreAlignedWithInstructors() {
        val body = """{"course_code":"CMSC132","sec_code":"0101","instructors":["Pedram Sadeghian","Instructor: TBA"],
            "meetings":["MWF-11:00am-11:50am-IRB-0324"],"open_seats":28,"total_seats":28,"waitlist":0,
            "holdfile":null,"instructor_slugs":["pedram-sadeghian",null]}"""
        val section = json.decodeFromString(SectionResponse.serializer(), body).toDomain()
        assertEquals(listOf("pedram-sadeghian", ""), section.instructorSlugs)
        assertEquals("pedram-sadeghian", section.slugFor(0))
        assertNull(section.slugFor(1))
        // TBA placeholders never become profile links
        assertEquals(listOf("Pedram Sadeghian" to "pedram-sadeghian"), section.instructorLinks)
    }

    @Test
    fun sectionWithoutSlugsFromOlderApiStillMaps() {
        val body = """{"course_code":"MATH141","sec_code":"0101","instructors":["Jane Doe"],"meetings":[]}"""
        val section = json.decodeFromString(SectionResponse.serializer(), body).toDomain()
        assertEquals(listOf(""), section.instructorSlugs)
        assertEquals(listOf("Jane Doe" to null), section.instructorLinks)
    }

    @Test
    fun schedulesSavedBeforeSlugsStillLoad() {
        // A persisted section from an earlier app version has no instructorSlugs
        val stored = """{"currentSchedule":[{"course":{"courseCode":"CMSC131","name":"OOP I","minCredits":4,
            "maxCredits":null,"description":null,"genEds":null,"conditions":null,"sections":null},
            "section":{"courseCode":"CMSC131","sectionCode":"0101","instructors":["Jane Doe"],"meetings":[],
            "openSeats":1,"totalSeats":2,"waitlist":0,"holdfile":null},"colorIndex":0}]}"""
        val data = json.decodeFromString(AppData.serializer(), stored)
        assertEquals(emptyList(), data.currentSchedule.single().section.instructorSlugs)
        assertEquals(emptyList(), data.reviewKeys)
    }

    @Test
    fun courseResponseStillParsesWithSections() {
        val body = """{"course_code":"CMSC433","name":"PL","min_credits":3,"sections":[{"course_code":"CMSC433",
            "sec_code":"0101","instructors":["Anwar Mamat"],"meetings":[],"instructor_slugs":["anwar-mamat"]}]}"""
        val course = json.decodeFromString(CourseResponse.serializer(), body).toDomain()
        assertEquals("anwar-mamat", course.sections!!.single().slugFor(0))
    }

    @Test
    fun submissionOmitsUnsetOptionalFields() {
        val body = json.encodeToString(
            SubmitReviewRequest.serializer(),
            SubmitReviewRequest(instructorSlug = "larry-herman", rating = 4.5f, email = "a@umd.edu")
        )
        assertEquals("""{"instructor_slug":"larry-herman","rating":4.5,"email":"a@umd.edu"}""", body)
    }

    @Test
    fun contentRangeTotals() {
        assertEquals(112, parseContentRangeTotal("0-24/112"))
        assertEquals(0, parseContentRangeTotal("*/0"))
        assertNull(parseContentRangeTotal("0-1/*"))
        assertNull(parseContentRangeTotal(null))
    }

    // ---- Review rules (mirroring the API's validation) ----

    @Test
    fun ratingsAreHalfStepsFromOneToFive() {
        listOf(1f, 1.5f, 3f, 4.5f, 5f).forEach { assertTrue(ReviewRules.isValidRating(it), "$it") }
        listOf(0f, 0.5f, 4.3f, 5.5f).forEach { assertFalse(ReviewRules.isValidRating(it), "$it") }
    }

    @Test
    fun onlyUmdAddressesAreAccepted() {
        assertNull(ReviewRules.emailError("testudo@terpmail.umd.edu"))
        assertNull(ReviewRules.emailError("Testudo@UMD.edu"))
        assertNull(ReviewRules.emailError(""))
        assertEquals("Use your terpmail.umd.edu or umd.edu address", ReviewRules.emailError("a@gmail.com"))
        assertEquals("Use your terpmail.umd.edu or umd.edu address", ReviewRules.emailError("a@cs.umd.edu"))
        assertEquals("That doesn't look like an email address", ReviewRules.emailError("not-an-email"))
    }

    @Test
    fun courseCodesMatchTheApiPattern() {
        assertTrue(ReviewRules.isValidCourseCode("CMSC132"))
        assertTrue(ReviewRules.isValidCourseCode("MUSC229A"))
        assertFalse(ReviewRules.isValidCourseCode("CMSC13"))
        assertFalse(ReviewRules.isValidCourseCode("cmsc132"))
    }

    @Test
    fun blockingIssuesComeInFormOrder() {
        val ready = ReviewDraft(rating = 4f, email = "t@umd.edu", agreedToPolicy = true)
        assertNull(ReviewRules.blockingIssue(ready))
        assertEquals("Pick a rating", ReviewRules.blockingIssue(ready.copy(rating = 0f)))
        assertEquals("Enter your UMD email to confirm the review", ReviewRules.blockingIssue(ready.copy(email = "")))
        assertEquals("Agree to the review policy to submit", ReviewRules.blockingIssue(ready.copy(agreedToPolicy = false)))
        assertEquals(
            "Title is limited to 120 characters",
            ReviewRules.blockingIssue(ready.copy(title = "x".repeat(121)))
        )
    }

    @Test
    fun lengthCountsCharactersNotUtf16Units() {
        // Four emoji are eight UTF-16 units but four characters to the server
        assertEquals(4, ReviewRules.length("🐢🐢🐢🐢"))
    }

    @Test
    fun statusesMapFromTheApi() {
        assertEquals(ReviewStatus.Approved, ReviewStatus.fromApi("approved"))
        assertEquals(ReviewStatus.Unknown, ReviewStatus.fromApi("something-new"))
    }

    @Test
    fun halfStepAndDateFormatting() {
        assertEquals("4.5", 4.5f.toHalfStepString())
        assertEquals("3.0", 3f.toHalfStepString())
        assertEquals("Mar 2026", formatIsoMonthYear("2026-03-14T12:00:00+00:00"))
        assertNull(formatIsoMonthYear("bad"))
    }

    // ---- Name search normalization (matches the API's name_norm) ----

    @Test
    fun namesNormalizeLikeTheServer() {
        assertEquals("o brien", normalizeNameForSearch("O'Brien"))
        assertEquals("jose", normalizeNameForSearch("José"))
        assertEquals("anwar m", normalizeNameForSearch("  Anwar  M. "))
    }

    // ---- Links ----

    @Test
    fun verifyLinksFromTheEmail() {
        val link = AppLinks.parse("https://www.jupiterp.com/review/verify?token=abc_DEF-123")
        assertIs<AppLink.VerifyReview>(link)
        assertEquals("abc_DEF-123", link.token)
        assertNull(AppLinks.parse("https://www.jupiterp.com/review/verify"))
    }

    @Test
    fun withdrawAndProfessorLinks() {
        assertEquals(AppLink.ManageReviews, AppLinks.parse("https://jupiterp.com/review/withdraw"))
        assertEquals(AppLink.Professor("larry-herman"), AppLinks.parse("https://jupiterp.com/professor/larry-herman"))
        assertEquals(AppLink.Professor("larry-herman"), AppLinks.parse("https://www.jupiterp.com/professor/Larry-Herman/"))
    }

    @Test
    fun otherLinksFallThroughToScheduleImport() {
        assertNull(AppLinks.parse("https://jupiterp.com/?s=2~CMSC4Aq8z"))
        assertNull(AppLinks.parse("https://example.com/professor/larry-herman"))
        assertNull(AppLinks.parse("https://jupiterp.com/professor/bad slug"))
        assertNull(AppLinks.parse("not a url"))
    }

    // ---- Instructor directory ----

    private fun section(names: List<String>, slugs: List<String>, open: Int = 5) = Section(
        courseCode = "CMSC132", sectionCode = "0101", instructors = names, meetings = emptyList(),
        openSeats = open, totalSeats = 30, waitlist = 0, holdfile = null, instructorSlugs = slugs
    )

    @Test
    fun slugBeatsNameSoNamesakesStayApart() {
        // Two different professors share a name; the section's slug decides
        val directory = InstructorDirectory().withInstructors(
            listOf(
                Instructor("William Martin", "william-martin", null, combinedRating = 2.1f),
                Instructor("William Martin", "william-martin-2", null, combinedRating = 4.6f)
            )
        )
        assertEquals(4.6f, directory.ratingFor(section(listOf("William Martin"), listOf("william-martin-2"))))
        assertEquals(2.1f, directory.ratingFor(section(listOf("William Martin"), listOf("william-martin"))))
    }

    @Test
    fun unresolvedNamesFallBackToNameLookup() {
        val directory = InstructorDirectory().withInstructors(listOf(Instructor("Jane Doe", "jane-doe", 3.5f)))
        assertEquals(3.5f, directory.ratingFor(section(listOf("Jane Doe"), listOf(""))))
    }

    @Test
    fun courseSummaryGroupsSectionsByProfessor() {
        val sections = listOf(
            section(listOf("A Prof"), listOf("a-prof"), open = 3),
            section(listOf("B Prof"), listOf("b-prof"), open = 0),
            section(listOf("A Prof"), listOf("a-prof"), open = 2)
        )
        val summaries = summarizeCourseInstructors(sections, InstructorDirectory(), grades = null)
        assertEquals(listOf("a-prof", "b-prof"), summaries.map { it.slug })
        assertEquals(2, summaries[0].sectionCount)
        assertEquals(5, summaries[0].openSeats)
        assertEquals(0, summaries[1].openSeats)
    }
}
