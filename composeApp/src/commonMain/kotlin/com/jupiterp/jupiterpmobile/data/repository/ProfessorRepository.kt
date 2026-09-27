package com.jupiterp.jupiterpmobile.data.repository

import com.jupiterp.jupiterpmobile.data.api.JupiterpApiClient
import com.jupiterp.jupiterpmobile.data.api.Page
import com.jupiterp.jupiterpmobile.data.model.GradeSummaryParams
import com.jupiterp.jupiterpmobile.data.model.InstructorSearchParams
import com.jupiterp.jupiterpmobile.data.model.SectionSearchParams
import com.jupiterp.jupiterpmobile.data.model.toDistribution
import com.jupiterp.jupiterpmobile.data.model.toDomain
import com.jupiterp.jupiterpmobile.domain.model.GradeDistribution
import com.jupiterp.jupiterpmobile.domain.model.Instructor
import com.jupiterp.jupiterpmobile.domain.model.ProfessorCourseRecord
import com.jupiterp.jupiterpmobile.domain.model.Review
import com.jupiterp.jupiterpmobile.domain.model.TermGpa
import com.jupiterp.jupiterpmobile.domain.model.normalizeNameForSearch

/**
 * Everything on a professor's profile. Always filtered by slug, never by
 * name: the same professor is spelled several ways across the registrar's
 * grade files, Testudo, and PlanetTerp, and an exact name match silently
 * misses a large share of them.
 */
class ProfessorRepository(
    private val apiClient: JupiterpApiClient
) {
    suspend fun getProfessor(slug: String): Result<Instructor?> =
        apiClient.getInstructors(InstructorSearchParams(instructorSlugs = listOf(slug), limit = 1))
            .map { rows -> rows.firstOrNull()?.toDomain() }

    /** Server-side partial-name search over every instructor on file. */
    suspend fun searchProfessors(query: String, limit: Int = 20): Result<List<Instructor>> {
        val normalized = normalizeNameForSearch(query)
        if (normalized.length < 2) return Result.success(emptyList())
        return apiClient.getInstructors(
            InstructorSearchParams(
                nameSearch = normalized,
                columns = "slug,name,average_rating,combined_rating,is_active,jupiterp_review_count",
                // Current professors first, then alphabetical
                sortBy = "is_active.desc,name.asc",
                limit = limit
            )
        ).map { rows -> rows.map { it.toDomain() } }
    }

    /** How this professor grades across every course they've taught. */
    suspend fun getOverallGrades(slug: String): Result<GradeDistribution?> =
        apiClient.getGradeSummary(
            GradeSummaryParams(groupBy = "instructorOverall", instructorSlug = slug, limit = 1)
        ).map { rows -> rows.firstOrNull()?.toDistribution() }

    /** One row per course they've taught, most students first. */
    suspend fun getCourseRecords(slug: String): Result<List<ProfessorCourseRecord>> =
        apiClient.getGradeSummary(
            GradeSummaryParams(groupBy = "instructor", instructorSlug = slug, sortBy = "graded.desc")
        ).map { rows ->
            rows.mapNotNull { row ->
                val code = row.courseCode ?: return@mapNotNull null
                ProfessorCourseRecord(code, row.toDistribution())
            }
        }

    /** GPA per term, oldest first, for the trend chart. */
    suspend fun getTermTrend(slug: String): Result<List<TermGpa>> =
        apiClient.getGradeSummary(
            GradeSummaryParams(groupBy = "instructorTerm", instructorSlug = slug, sortBy = "term.asc", limit = 200)
        ).map { rows ->
            rows.mapNotNull { row ->
                val term = row.term ?: return@mapNotNull null
                val gpa = row.gpa ?: return@mapNotNull null
                TermGpa(term, gpa, row.graded)
            }
        }

    /**
     * Courses they're scheduled to teach in the term the API is serving.
     * Distinct from [getCourseRecords], which comes from grade data and so is
     * always at least a term behind.
     */
    suspend fun getCurrentCourses(slug: String): Result<List<String>> =
        apiClient.getSections(SectionSearchParams(instructorSlug = slug, limit = 500))
            .map { sections -> sections.map { it.courseCode }.distinct().sorted() }

    /** Approved reviews, newest first. */
    suspend fun getReviews(
        slug: String,
        courseCode: String? = null,
        offset: Int = 0,
        limit: Int = 20
    ): Result<Page<Review>> =
        apiClient.getReviews(slug, courseCode, limit, offset)
            .map { page -> Page(page.data.map { it.toDomain() }, page.total) }
}
