package com.jupiterp.jupiterpmobile.data.api

import com.jupiterp.jupiterpmobile.data.model.ApiErrorResponse
import com.jupiterp.jupiterpmobile.data.model.CourseMinifiedResponse
import com.jupiterp.jupiterpmobile.data.model.CourseResponse
import com.jupiterp.jupiterpmobile.data.model.CourseSearchParams
import com.jupiterp.jupiterpmobile.data.model.DepartmentResponse
import com.jupiterp.jupiterpmobile.data.model.GradeSummaryParams
import com.jupiterp.jupiterpmobile.data.model.GradeSummaryResponse
import com.jupiterp.jupiterpmobile.data.model.InstructorResponse
import com.jupiterp.jupiterpmobile.data.model.InstructorSearchParams
import com.jupiterp.jupiterpmobile.data.model.ManagedReviewResponse
import com.jupiterp.jupiterpmobile.data.model.ReportReviewRequest
import com.jupiterp.jupiterpmobile.data.model.ReviewResponse
import com.jupiterp.jupiterpmobile.data.model.SectionResponse
import com.jupiterp.jupiterpmobile.data.model.SectionSearchParams
import com.jupiterp.jupiterpmobile.data.model.SubmitReviewRequest
import com.jupiterp.jupiterpmobile.data.model.VerifyReviewResponse
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Ktor HTTP client for the Jupiterp API v1
 *
 * API Documentation: https://api.jupiterp.com/
 *
 * Reads are open and cached server-side. The review writes are
 * pre-moderated: nothing submitted is public until a moderator approves it.
 */
class JupiterpApiClient {

    companion object {
        // Note: every request path below starts with "/v1/...". Ktor replaces
        // the default URL's path when the request path is absolute, so the
        // version segment must live in the paths, not in the base URL.
        private const val BASE_URL = "https://api.jupiterp.com"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        // Optional review fields are left out of the body rather than sent as null
        explicitNulls = false
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.NONE
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }

        defaultRequest {
            url(BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * GET /v1/courses
     * Get a list of courses with full course info (without sections)
     */
    suspend fun getCourses(params: CourseSearchParams = CourseSearchParams()): Result<List<CourseResponse>> = runCatching {
        val response = client.get("/v1/courses") {
            applyCommonCourseParams(params)
        }
        decodeList<CourseResponse>(response)
    }

    /**
     * GET /v1/courses/minified
     * Get a list of courses with just the code and title
     */
    suspend fun getCoursesMinified(params: CourseSearchParams = CourseSearchParams()): Result<List<CourseMinifiedResponse>> = runCatching {
        val response = client.get("/v1/courses/minified") {
            applyCommonCourseParams(params)
        }
        decodeList<CourseMinifiedResponse>(response)
    }

    /**
     * GET /v1/courses/withSections
     * Get courses with their sections
     */
    suspend fun getCoursesWithSections(
        params: CourseSearchParams = CourseSearchParams(),
        sectionParams: SectionSearchParams = SectionSearchParams()
    ): Result<List<CourseResponse>> = runCatching {
        val response = client.get("/v1/courses/withSections") {
            applyCommonCourseParams(params)
            // Additional section filters
            sectionParams.totalClassSize?.forEach { parameter("totalClassSize", it) }
            sectionParams.onlyOpen?.let { parameter("onlyOpen", it) }
            sectionParams.instructor?.let { parameter("instructor", it) }
        }
        decodeList<CourseResponse>(response)
    }

    /**
     * GET /v1/sections
     * Get sections for specific courses, or for one instructor by slug
     */
    suspend fun getSections(params: SectionSearchParams = SectionSearchParams()): Result<List<SectionResponse>> = runCatching {
        val response = client.get("/v1/sections") {
            params.courseCodes?.let { parameter("courseCodes", it.joinToString(",")) }
            params.prefix?.let { parameter("prefix", it) }
            params.totalClassSize?.forEach { parameter("totalClassSize", it) }
            params.onlyOpen?.let { parameter("onlyOpen", it) }
            params.instructor?.let { parameter("instructor", it) }
            params.instructorSlug?.let { parameter("instructorSlug", it) }
            parameter("limit", params.limit)
            parameter("offset", params.offset)
            params.sortBy?.let { parameter("sortBy", it) }
        }
        decodeList<SectionResponse>(response)
    }

    /**
     * GET /v1/instructors
     * Get a list of all instructors and their ratings
     */
    suspend fun getInstructors(params: InstructorSearchParams = InstructorSearchParams()): Result<List<InstructorResponse>> = runCatching {
        val response = client.get("/v1/instructors") {
            applyInstructorParams(params)
        }
        decodeList<InstructorResponse>(response)
    }

    /**
     * GET /v1/instructors/active
     * Get instructors currently teaching a course
     */
    suspend fun getActiveInstructors(params: InstructorSearchParams = InstructorSearchParams()): Result<List<InstructorResponse>> = runCatching {
        val response = client.get("/v1/instructors/active") {
            applyInstructorParams(params)
        }
        decodeList<InstructorResponse>(response)
    }

    /**
     * GET /v1/deptList
     * Get a list of 4-letter department codes
     */
    suspend fun getDepartments(): Result<List<DepartmentResponse>> = runCatching {
        val response = client.get("/v1/deptList")
        decodeList<DepartmentResponse>(response)
    }

    /**
     * GET /v1/grades/summary
     * Grade distributions summed by course, term, or instructor. Data is UMD
     * registrar releases (Fall and Spring only), aggregated server-side.
     */
    suspend fun getGradeSummary(params: GradeSummaryParams): Result<List<GradeSummaryResponse>> = runCatching {
        val response = client.get("/v1/grades/summary") {
            parameter("groupBy", params.groupBy)
            params.courseCodes?.let { parameter("courseCodes", it.joinToString(",")) }
            params.instructorSlug?.let { parameter("instructorSlug", it) }
            params.minStudents?.let { parameter("minStudents", it) }
            params.sortBy?.let { parameter("sortBy", it) }
            parameter("limit", params.limit)
        }
        decodeList<GradeSummaryResponse>(response)
    }

    /**
     * GET /v1/reviews
     * Approved reviews for one professor, newest first. The total comes back
     * in the `Content-Range` header.
     */
    suspend fun getReviews(
        instructorSlug: String,
        courseCode: String? = null,
        limit: Int = 25,
        offset: Int = 0
    ): Result<Page<ReviewResponse>> = runCatching {
        val response = client.get("/v1/reviews") {
            parameter("instructorSlug", instructorSlug)
            courseCode?.let { parameter("courseCode", it) }
            parameter("limit", limit)
            parameter("offset", offset)
        }
        Page(
            data = decodeList<ReviewResponse>(response),
            total = parseContentRangeTotal(response.headers["Content-Range"])
        )
    }

    /**
     * POST /v1/reviews
     * Answers 202 whether or not this address has already reviewed this
     * professor, so the result can't be read as anything finer than "accepted".
     */
    suspend fun submitReview(request: SubmitReviewRequest): Result<Unit> = runCatching {
        val response = client.post("/v1/reviews") {
            setBody(request)
        }
        ensureSuccess(response)
    }

    /**
     * GET /v1/reviews/verify/:token
     * Confirms an emailed link. Returns the manage key exactly once; a repeat
     * visit answers `already_verified` without one.
     */
    suspend fun verifyReview(token: String): Result<VerifyReviewResponse> = runCatching {
        val response = client.get("/v1/reviews/verify/${token.encodeURLPathPart()}")
        ensureSuccess(response)
        json.decodeFromString(VerifyReviewResponse.serializer(), response.bodyAsText())
    }

    /**
     * GET /v1/reviews/manage
     * The review a manage key controls. The key alone identifies it.
     */
    suspend fun getManagedReview(manageKey: String): Result<ManagedReviewResponse> = runCatching {
        val response = client.get("/v1/reviews/manage") {
            bearerAuth(manageKey)
        }
        ensureSuccess(response)
        json.decodeFromString(ManagedReviewResponse.serializer(), response.bodyAsText())
    }

    /**
     * DELETE /v1/reviews/:id
     * Withdraws a review. Idempotent; the text is nulled server-side.
     */
    suspend fun withdrawReview(reviewId: String, manageKey: String): Result<Unit> = runCatching {
        val response = client.delete("/v1/reviews/${reviewId.encodeURLPathPart()}") {
            bearerAuth(manageKey)
        }
        ensureSuccess(response)
    }

    /**
     * POST /v1/reviews/:id/report
     * Flags a published review for a moderator.
     */
    suspend fun reportReview(reviewId: String, request: ReportReviewRequest): Result<Unit> = runCatching {
        val response = client.post("/v1/reviews/${reviewId.encodeURLPathPart()}/report") {
            setBody(request)
        }
        ensureSuccess(response)
    }

    // Helper functions for common parameter handling

    private fun HttpRequestBuilder.applyCommonCourseParams(params: CourseSearchParams) {
        params.courseCodes?.let { parameter("courseCodes", it.joinToString(",")) }
        params.prefix?.let { parameter("prefix", it) }
        params.number?.let { parameter("number", it) }
        params.genEds?.let { parameter("genEds", it.joinToString(",")) }
        params.credits?.forEach { parameter("credits", it) }
        parameter("limit", params.limit)
        parameter("offset", params.offset)
        params.sortBy?.let { parameter("sortBy", it) }
    }

    private fun HttpRequestBuilder.applyInstructorParams(params: InstructorSearchParams) {
        params.instructorNames?.let { parameter("instructorNames", it.joinToString(",")) }
        params.instructorSlugs?.let { parameter("instructorSlugs", it.joinToString(",")) }
        params.nameSearch?.let { parameter("nameSearch", it) }
        params.activeOnly?.let { parameter("activeOnly", it) }
        params.columns?.let { parameter("columns", it) }
        params.ratings?.forEach { parameter("ratings", it) }
        parameter("limit", params.limit)
        parameter("offset", params.offset)
        params.sortBy?.let { parameter("sortBy", it) }
    }

    private suspend inline fun <reified T> decodeList(response: HttpResponse): List<T> {
        ensureSuccess(response)
        return json.decodeFromString(ListSerializer(serializer<T>()), response.bodyAsText())
    }

    /**
     * Turns a non-2xx response into an [ApiException] carrying the API's own
     * message, which is written for people and safe to show.
     */
    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) return
        val message = runCatching {
            json.decodeFromString(ApiErrorResponse.serializer(), response.bodyAsText()).error
        }.getOrNull()
        throw ApiException(response.status.value, message)
    }

    /**
     * Convenience method: Search courses by prefix (e.g., "CMSC1" for all CMSC1XX courses)
     */
    suspend fun searchByPrefix(prefix: String, limit: Int = 100): Result<List<CourseResponse>> {
        return getCoursesWithSections(
            params = CourseSearchParams(prefix = prefix, limit = limit)
        )
    }

    /**
     * Convenience method: Search courses by department and get sections
     */
    suspend fun searchByDepartment(department: String, limit: Int = 100): Result<List<CourseResponse>> {
        return getCoursesWithSections(
            params = CourseSearchParams(prefix = department, limit = limit)
        )
    }

    /**
     * Convenience method: Get courses by Gen-Ed requirements
     */
    suspend fun searchByGenEds(genEds: List<String>, limit: Int = 100): Result<List<CourseResponse>> {
        return getCoursesWithSections(
            params = CourseSearchParams(genEds = genEds, limit = limit)
        )
    }

    /**
     * Convenience method: Get specific courses by course codes
     */
    suspend fun getCoursesByCodes(courseCodes: List<String>): Result<List<CourseResponse>> {
        return getCoursesWithSections(
            params = CourseSearchParams(courseCodes = courseCodes)
        )
    }

    /**
     * Convenience method: Get instructor rating by name
     */
    suspend fun getInstructorByName(name: String): Result<InstructorResponse?> =
        getInstructors(
            InstructorSearchParams(instructorNames = listOf(name), limit = 1)
        ).map { it.firstOrNull() }

    fun close() {
        client.close()
    }
}

/**
 * A non-2xx answer from the API. [apiMessage] is the API's `error` field,
 * which it phrases for end users ("rating must be between 1 and 5 in half
 * steps, like 3.5").
 */
class ApiException(
    val status: Int,
    val apiMessage: String?
) : Exception(apiMessage ?: "Request failed ($status)") {
    val isRateLimited: Boolean get() = status == 429
}

/** A page of results, with the total when the server reported one. */
data class Page<T>(
    val data: List<T>,
    val total: Int?
)

/**
 * Reads the total out of a PostgREST `Content-Range` header such as
 * `0-24/112`. The total is `*` when no count was requested, and `*\/0` is how
 * an empty exact count arrives.
 */
fun parseContentRangeTotal(header: String?): Int? {
    val total = header?.substringAfter('/', missingDelimiterValue = "") ?: return null
    return total.toIntOrNull()
}

/**
 * Sealed class for API results with loading state
 */
sealed class ApiState<out T> {
    data object Loading : ApiState<Nothing>()
    data class Success<T>(val data: T) : ApiState<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : ApiState<Nothing>()
    data object Empty : ApiState<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isEmpty: Boolean get() = this is Empty

    fun getOrNull(): T? = (this as? Success)?.data
}
