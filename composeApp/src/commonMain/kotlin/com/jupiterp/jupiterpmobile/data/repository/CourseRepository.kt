package com.jupiterp.jupiterpmobile.data.repository

import com.jupiterp.jupiterpmobile.data.api.JupiterpApiClient
import com.jupiterp.jupiterpmobile.data.model.CourseSearchParams
import com.jupiterp.jupiterpmobile.data.model.InstructorSearchParams
import com.jupiterp.jupiterpmobile.data.model.SectionSearchParams
import com.jupiterp.jupiterpmobile.data.model.toDomain
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.Department
import com.jupiterp.jupiterpmobile.domain.model.Instructor
import com.jupiterp.jupiterpmobile.domain.model.Section
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Repository for course-related data operations
 * Uses the Jupiterp API v1
 */
class CourseRepository(
    private val apiClient: JupiterpApiClient
) {
    private companion object {
        const val SUGGESTION_COLUMNS = "slug,name,average_rating,combined_rating"
    }

    /**
     * Search courses with filters and include section data
     *
     * @param query Search query - used as prefix (e.g., "CMSC1" for CMSC1XX courses)
     * @param department Department prefix (e.g., "CMSC")
     * @param genEds List of Gen-Ed codes to filter by
     * @param instructor Filter by instructor name
     * @param onlyOpen If true, only return courses with sections that have open seats
     * @param limit Maximum number of results
     */
    suspend fun searchCourses(
        query: String? = null,
        department: String? = null,
        genEds: List<String>? = null,
        instructor: String? = null,
        onlyOpen: Boolean? = null,
        limit: Int = 100
    ): Result<List<Course>> {
        val prefix = when {
            !query.isNullOrBlank() -> query.uppercase()
            !department.isNullOrBlank() -> department.uppercase()
            else -> null
        }

        // Without a prefix, /courses/withSections ignores the instructor filter and returns nothing.
        // Route through sections first instead.
        if (prefix == null && genEds.isNullOrEmpty() && !instructor.isNullOrBlank()) {
            return searchCoursesByInstructor(instructor, onlyOpen, limit)
        }

        val courseParams = CourseSearchParams(
            prefix = prefix,
            genEds = genEds?.takeIf { it.isNotEmpty() },
            limit = limit
        )

        val sectionParams = SectionSearchParams(
            instructor = instructor,
            onlyOpen = onlyOpen
        )

        return apiClient.getCoursesWithSections(courseParams, sectionParams).map { courses ->
            courses.map { it.toDomain() }
        }
    }

    /**
     * Search courses by specific course codes
     */
    suspend fun getCoursesByCodes(courseCodes: List<String>): Result<List<Course>> {
        return apiClient.getCoursesByCodes(courseCodes).map { courses ->
            courses.map { it.toDomain() }
        }
    }

    /**
     * Search courses by Gen-Ed requirements
     */
    suspend fun searchByGenEds(genEds: List<String>, limit: Int = 100): Result<List<Course>> {
        return apiClient.searchByGenEds(genEds, limit).map { courses ->
            courses.map { it.toDomain() }
        }
    }

    /**
     * Get all departments
     */
    suspend fun getDepartments(): Result<List<Department>> {
        return apiClient.getDepartments().map { departments ->
            departments.map { it.toDomain() }.sortedBy { it.code }
        }
    }

    /**
     * Fetch active instructors for autocomplete. The API caps each request at 500 and
     * returns ~2.6k entries unsorted, so we pull pages in parallel and sort client-side.
     * Only the columns autocomplete and ratings read are requested; the full v1
     * row is ~20 columns, most of it provenance the app never shows.
     */
    suspend fun getAllInstructorsForSuggestions(): Result<List<Instructor>> = runCatching {
        coroutineScope {
            val pageSize = 500
            val maxPages = 8
            (0 until maxPages).map { idx ->
                async {
                    apiClient.getActiveInstructors(
                        InstructorSearchParams(
                            columns = SUGGESTION_COLUMNS,
                            limit = pageSize,
                            offset = idx * pageSize
                        )
                    ).getOrNull().orEmpty()
                }
            }.awaitAll()
                .flatten()
                .distinctBy { it.slug }
                .map { it.toDomain() }
                .sortedBy { it.name }
        }
    }

    /**
     * The term whose sections the API is serving, e.g. 202701. The scraper
     * stamps `last_seen_term` on every instructor it sees, so the newest one
     * among active instructors is the current scrape.
     */
    suspend fun getServedTerm(): Int? =
        apiClient.getInstructors(
            InstructorSearchParams(
                activeOnly = true,
                // slug and name too: InstructorResponse requires them to decode
                columns = "slug,name,last_seen_term",
                sortBy = "last_seen_term.desc",
                limit = 1
            )
        ).getOrNull()?.firstOrNull()?.lastSeenTerm

    /**
     * Get all active instructors (teaching this semester)
     */
    suspend fun getActiveInstructors(limit: Int = 500): Result<List<Instructor>> {
        return apiClient.getActiveInstructors(
            InstructorSearchParams(limit = limit)
        ).map { instructors ->
            instructors.map { it.toDomain() }.sortedBy { it.name }
        }
    }

    /**
     * When only instructor is provided (no course prefix), the /courses/withSections
     * endpoint returns nothing. Use a two-step lookup instead.
     */
    suspend fun searchCoursesByInstructor(
        instructor: String,
        onlyOpen: Boolean? = null,
        limit: Int = 100
    ): Result<List<Course>> {
        val sectionsResult = apiClient.getSections(
            SectionSearchParams(instructor = instructor, onlyOpen = onlyOpen, limit = limit)
        )
        val courseCodes = sectionsResult.getOrElse { return Result.failure(it) }
            .map { it.courseCode }.distinct()
        if (courseCodes.isEmpty()) return Result.success(emptyList())
        return apiClient.getCoursesWithSections(CourseSearchParams(courseCodes = courseCodes))
            .map { courses -> courses.map { it.toDomain() } }
    }

    /**
     * Search instructors by name
     */
    suspend fun searchInstructors(names: List<String>): Result<List<Instructor>> {
        return apiClient.getInstructors(
            InstructorSearchParams(instructorNames = names)
        ).map { instructors ->
            instructors.map { it.toDomain() }
        }
    }

    /**
     * Instructor records for the given slugs, chunked to keep the query
     * string a sane length. Failed chunks are skipped: ratings are optional
     * metadata and one bad page shouldn't blank the rest.
     */
    suspend fun getInstructorsBySlugs(slugs: Collection<String>): List<Instructor> = coroutineScope {
        slugs
            .filter { it.isNotBlank() }
            .distinct()
            .chunked(50)
            .map { chunk ->
                async {
                    apiClient.getInstructors(
                        InstructorSearchParams(
                            instructorSlugs = chunk,
                            columns = SUGGESTION_COLUMNS,
                            limit = chunk.size
                        )
                    ).getOrNull().orEmpty()
                }
            }
            .awaitAll()
            .flatten()
            .map { it.toDomain() }
    }

    /**
     * Best-effort rating lookup for the sections' instructors, keyed by name
     * (which is what the schedule engine matches on) but resolved by slug:
     * `instructorSlugs[i]` is the professor the API resolved for slot i, so a
     * Testudo spelling that differs from the canonical record still gets its
     * rating, and two professors sharing a name are no longer conflated.
     * Names with no slug fall back to an exact-name lookup.
     */
    suspend fun getInstructorRatings(sections: List<Section>): Map<String, Float> = coroutineScope {
        val nameToSlug = mutableMapOf<String, String>()
        val unresolved = mutableSetOf<String>()
        sections.forEach { section ->
            section.instructorLinks.forEach { (name, slug) ->
                if (slug != null) nameToSlug.getOrPut(name) { slug } else unresolved += name
            }
        }
        unresolved.removeAll(nameToSlug.keys)

        val bySlug = async { getInstructorsBySlugs(nameToSlug.values).associateBy { it.slug } }
        val byName = async { getInstructorRatingsByName(unresolved.toList()) }

        val slugRatings = bySlug.await()
        val resolved = nameToSlug.mapNotNull { (name, slug) ->
            slugRatings[slug]?.rating?.let { name to it }
        }.toMap()
        byName.await() + resolved
    }

    /**
     * Best-effort bulk rating lookup keyed by instructor name. Requests are
     * chunked to keep the name-list query parameter a sane length; failed
     * chunks are skipped rather than failing the whole lookup, since ratings
     * are optional metadata.
     */
    private suspend fun getInstructorRatingsByName(names: List<String>): Map<String, Float> = coroutineScope {
        names
            .filter { it.isNotBlank() && !it.contains("TBA", ignoreCase = true) }
            .distinct()
            .chunked(50)
            .map { chunk ->
                async {
                    apiClient.getInstructors(
                        InstructorSearchParams(instructorNames = chunk, limit = chunk.size)
                    ).getOrNull().orEmpty()
                }
            }
            .awaitAll()
            .flatten()
            .map { it.toDomain() }
            .mapNotNull { instructor -> instructor.rating?.let { instructor.name to it } }
            .toMap()
    }

    /**
     * Get instructor by exact name
     */
    suspend fun getInstructorByName(name: String): Result<Instructor?> {
        return apiClient.getInstructorByName(name).map { response ->
            response?.toDomain()
        }
    }
}