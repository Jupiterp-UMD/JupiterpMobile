package com.jupiterp.jupiterpmobile.data.repository

import com.jupiterp.jupiterpmobile.data.api.JupiterpApiClient
import com.jupiterp.jupiterpmobile.data.model.GradeSummaryParams
import com.jupiterp.jupiterpmobile.data.model.toDistribution
import com.jupiterp.jupiterpmobile.domain.model.CourseGrades
import com.jupiterp.jupiterpmobile.domain.model.CourseGradesState
import com.jupiterp.jupiterpmobile.domain.model.InstructorCourseGrades
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Course grade distributions, cached for the life of the process. The API
 * puts a twelve-hour cache in front of these and they only change when a new
 * term's release is ingested, so there's nothing to gain from refetching.
 *
 * Grade data is supplementary: a failure is recorded as [CourseGradesState.Error]
 * for that course and never propagates into search.
 */
class GradesRepository(
    private val apiClient: JupiterpApiClient
) {
    private val _courseGrades = MutableStateFlow<Map<String, CourseGradesState>>(emptyMap())
    val courseGrades: StateFlow<Map<String, CourseGradesState>> = _courseGrades.asStateFlow()

    /**
     * Loads grades for [courseCode] unless they're loaded, loading, or known
     * absent. An earlier error is retried only when [retryError] is set, so a
     * card scrolling back into view doesn't hammer a failing endpoint.
     */
    suspend fun ensureCourseGrades(courseCode: String, retryError: Boolean = false) {
        var claimed = false
        _courseGrades.update { current ->
            val existing = current[courseCode]
            if (existing == null || (existing is CourseGradesState.Error && retryError)) {
                claimed = true
                current + (courseCode to CourseGradesState.Loading)
            } else {
                claimed = false
                current
            }
        }
        // Another caller already owns this fetch (the update above is atomic,
        // so exactly one caller claims each course)
        if (!claimed) return

        val result = try {
            fetchCourseGrades(courseCode)
        } catch (e: CancellationException) {
            // Release the claim so the next caller can try again
            _courseGrades.update { it - courseCode }
            throw e
        }
        _courseGrades.update { it + (courseCode to result) }
    }

    private suspend fun fetchCourseGrades(courseCode: String): CourseGradesState = coroutineScope {
        // Two requests: the course-wide figure includes sections with no
        // attributed instructor, so it can't be derived from the per-professor rows
        val courseRows = async {
            apiClient.getGradeSummary(GradeSummaryParams(groupBy = "course", courseCodes = listOf(courseCode)))
        }
        val instructorRows = async {
            apiClient.getGradeSummary(GradeSummaryParams(groupBy = "instructor", courseCodes = listOf(courseCode)))
        }

        val course = courseRows.await().getOrElse { return@coroutineScope CourseGradesState.Error }
            .firstOrNull() ?: return@coroutineScope CourseGradesState.None

        // Per-professor rows are a bonus; the course figure stands on its own
        val byInstructor = instructorRows.await().getOrNull().orEmpty()
            .mapNotNull { row ->
                val slug = row.instructorSlug ?: return@mapNotNull null
                InstructorCourseGrades(
                    instructorSlug = slug,
                    instructorName = row.instructor ?: slug,
                    distribution = row.toDistribution()
                )
            }
            .associateBy { it.instructorSlug }

        CourseGradesState.Loaded(
            CourseGrades(
                courseCode = courseCode,
                course = course.toDistribution(),
                byInstructorSlug = byInstructor
            )
        )
    }
}
