package com.jupiterp.jupiterpmobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API Response models for the Jupiterp API v1
 * These models map directly to the API responses
 *
 * API Docs: https://api.jupiterp.com/
 */

/**
 * Course response from /v1/courses or /v1/courses/withSections
 */
@Serializable
data class CourseResponse(
    @SerialName("course_code") val courseCode: String,
    val name: String,
    @SerialName("min_credits") val minCredits: Int,
    @SerialName("max_credits") val maxCredits: Int? = null,
    @SerialName("gen_eds") val genEds: List<String>? = null,
    val conditions: List<String>? = null,
    val description: String? = null,
    // Only present in /v1/courses/withSections response
    val sections: List<SectionResponse>? = null
)

/**
 * Minified course response from /v1/courses/minified
 */
@Serializable
data class CourseMinifiedResponse(
    @SerialName("course_code") val courseCode: String,
    val name: String
)

/**
 * Section response from /v1/sections or embedded in CourseResponse
 *
 * Meeting string formats:
 * - In-person: "Days-StartTime-EndTime-Building-Room" (e.g., "TuTh-11:00am-12:15pm-CSI-1115")
 * - Online sync: "Days-StartTime-EndTime-OnlineSync"
 * - Online async: "OnlineAsync"
 * - Unspecified: "Unspecified"
 */
@Serializable
data class SectionResponse(
    @SerialName("course_code") val courseCode: String,
    @SerialName("sec_code") val secCode: String,
    val instructors: List<String> = emptyList(),
    val meetings: List<String> = emptyList(),
    @SerialName("open_seats") val openSeats: Int = 0,
    @SerialName("total_seats") val totalSeats: Int = 0,
    val waitlist: Int = 0,
    val holdfile: Int? = null,
    // Parallel to `instructors`: slot i is the Jupiterp slug the API resolved
    // for instructors[i] through its alias table. Empty strings or a short
    // list mean the name could not be resolved.
    @SerialName("instructor_slugs") val instructorSlugs: List<String?> = emptyList()
)

/**
 * Instructor response from /v1/instructors or /v1/instructors/active
 *
 * `slug` is Jupiterp's own identifier (and the professor page URL segment);
 * PlanetTerp's slug moved to `pt_slug`. `average_rating` is kept for v0
 * compatibility and mirrors `combined_rating`, which blends the frozen
 * PlanetTerp snapshot with approved Jupiterp reviews.
 */
@Serializable
data class InstructorResponse(
    val slug: String,
    val name: String,
    @SerialName("average_rating") val averageRating: Float? = null,
    val id: Long? = null,
    @SerialName("combined_rating") val combinedRating: Float? = null,
    @SerialName("pt_average_rating") val ptAverageRating: Float? = null,
    @SerialName("pt_review_count") val ptReviewCount: Int? = null,
    @SerialName("jupiterp_rating") val jupiterpRating: Float? = null,
    @SerialName("jupiterp_review_count") val jupiterpReviewCount: Int = 0,
    @SerialName("first_seen_term") val firstSeenTerm: Int? = null,
    @SerialName("last_seen_term") val lastSeenTerm: Int? = null,
    @SerialName("is_active") val isActive: Boolean = false
)

/**
 * One row of /v1/grades/summary. Every grouping returns the fifteen grade
 * buckets plus `total`, `graded`, and `gpa`; the remaining fields are present
 * only for the groupings that carry them.
 */
@Serializable
data class GradeSummaryResponse(
    @SerialName("course_code") val courseCode: String? = null,
    val term: Int? = null,
    val instructor: String? = null,
    @SerialName("instructor_id") val instructorId: Long? = null,
    @SerialName("instructor_slug") val instructorSlug: String? = null,
    @SerialName("course_count") val courseCount: Int? = null,
    @SerialName("section_count") val sectionCount: Int = 0,
    @SerialName("term_count") val termCount: Int? = null,
    @SerialName("first_term") val firstTerm: Int? = null,
    @SerialName("last_term") val lastTerm: Int? = null,
    val total: Int = 0,
    val graded: Int = 0,
    @SerialName("a_plus") val aPlus: Int = 0,
    val a: Int = 0,
    @SerialName("a_minus") val aMinus: Int = 0,
    @SerialName("b_plus") val bPlus: Int = 0,
    val b: Int = 0,
    @SerialName("b_minus") val bMinus: Int = 0,
    @SerialName("c_plus") val cPlus: Int = 0,
    val c: Int = 0,
    @SerialName("c_minus") val cMinus: Int = 0,
    @SerialName("d_plus") val dPlus: Int = 0,
    val d: Int = 0,
    @SerialName("d_minus") val dMinus: Int = 0,
    val f: Int = 0,
    val w: Int = 0,
    val other: Int = 0,
    val gpa: Float? = null
)

/**
 * A published review from GET /v1/reviews. Only approved reviews are ever
 * returned; the view behind it has no reviewer identity columns at all.
 */
@Serializable
data class ReviewResponse(
    val id: String,
    @SerialName("instructor_slug") val instructorSlug: String? = null,
    @SerialName("course_code") val courseCode: String? = null,
    val term: Int? = null,
    val rating: Float,
    @SerialName("expected_grade") val expectedGrade: String? = null,
    val title: String? = null,
    val body: String? = null,
    @SerialName("submitted_at") val submittedAt: String,
    @SerialName("edited_at") val editedAt: String? = null
)

/**
 * Body of POST /v1/reviews. Optional fields are omitted rather than sent as
 * null, matching what the site sends.
 */
@Serializable
data class SubmitReviewRequest(
    @SerialName("instructor_slug") val instructorSlug: String,
    val rating: Float,
    val email: String,
    @SerialName("course_code") val courseCode: String? = null,
    val term: Int? = null,
    @SerialName("expected_grade") val expectedGrade: String? = null,
    val title: String? = null,
    val body: String? = null,
    @SerialName("captcha_token") val captchaToken: String? = null
)

/** Reply from GET /v1/reviews/verify/:token. `manage_key` is present once. */
@Serializable
data class VerifyReviewResponse(
    val status: String? = null,
    val message: String? = null,
    @SerialName("manage_key") val manageKey: String? = null,
    val error: String? = null
)

/** The review a manage key controls, from GET /v1/reviews/manage. */
@Serializable
data class ManagedReviewResponse(
    val id: String,
    val instructor: String = "",
    @SerialName("instructor_slug") val instructorSlug: String = "",
    @SerialName("course_code") val courseCode: String? = null,
    val term: Int? = null,
    val rating: Float,
    val title: String? = null,
    val body: String? = null,
    val status: String,
    @SerialName("submitted_at") val submittedAt: String = "",
    val withdrawable: Boolean = false
)

/** Body of POST /v1/reviews/:id/report. */
@Serializable
data class ReportReviewRequest(
    val reason: String,
    val detail: String = ""
)

/** Every error the API writes is `{"error": "..."}`, phrased for people. */
@Serializable
data class ApiErrorResponse(
    val error: String? = null
)

/**
 * Department response from /v1/deptList
 */
@Serializable
data class DepartmentResponse(
    @SerialName("dept_code") val deptCode: String,
    val name: String
)

/**
 * Search/Filter parameters for course queries
 */
data class CourseSearchParams(
    val courseCodes: List<String>? = null,
    val prefix: String? = null,
    val number: String? = null,
    val genEds: List<String>? = null,
    val credits: List<String>? = null, // e.g., ["gt.2", "lt.5"]
    val limit: Int = 100,
    val offset: Int = 0,
    val sortBy: String? = null // e.g., "name.asc,min_credits.desc"
)

/**
 * Search/Filter parameters for section queries
 */
data class SectionSearchParams(
    val courseCodes: List<String>? = null,
    val prefix: String? = null,
    val totalClassSize: List<String>? = null, // e.g., ["gt.40", "lt.100"]
    val onlyOpen: Boolean? = null,
    val instructor: String? = null,
    // Resolved through the instructor alias table, so spelling differences
    // between Testudo and the canonical record don't matter
    val instructorSlug: String? = null,
    val limit: Int = 100,
    val offset: Int = 0,
    val sortBy: String? = null
)

/**
 * Search/Filter parameters for instructor queries
 */
data class InstructorSearchParams(
    val instructorNames: List<String>? = null,
    val instructorSlugs: List<String>? = null,
    // Case-insensitive substring match against a normalized name column
    val nameSearch: String? = null,
    val activeOnly: Boolean? = null,
    // Comma-separated column list; trims the ~20-column row when only a few are read
    val columns: String? = null,
    val ratings: List<String>? = null, // e.g., ["gt.3.5", "lt.5"]
    val limit: Int = 100,
    val offset: Int = 0,
    val sortBy: String? = null
)

/**
 * Parameters for /v1/grades/summary. `groupBy` is one of `course`, `term`,
 * `instructor`, `instructorOverall`, or `instructorTerm`.
 */
data class GradeSummaryParams(
    val groupBy: String = "course",
    val courseCodes: List<String>? = null,
    val instructorSlug: String? = null,
    val minStudents: Int? = null,
    val limit: Int = 500,
    val sortBy: String? = null
)