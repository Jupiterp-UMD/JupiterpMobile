package com.jupiterp.jupiterpmobile.domain.model

/**
 * Known instructors, keyed by Jupiterp slug with a by-name fallback for
 * section instructors the API couldn't resolve to a slug.
 *
 * Slug first: Testudo's spelling and the canonical record disagree often
 * enough to matter, and two real professors can share a name outright.
 */
data class InstructorDirectory(
    val bySlug: Map<String, Instructor> = emptyMap(),
    val byName: Map<String, Instructor> = emptyMap()
) {
    fun lookup(name: String, slug: String?): Instructor? =
        slug?.let { bySlug[it] } ?: byName[name]

    /** Co-taught sections show the first instructor that has a rating. */
    fun ratingFor(section: Section): Float? =
        section.instructorLinks.firstNotNullOfOrNull { (name, slug) -> lookup(name, slug)?.rating }

    fun withInstructors(instructors: Collection<Instructor>): InstructorDirectory {
        if (instructors.isEmpty()) return this
        return copy(
            bySlug = bySlug + instructors.associateBy { it.slug },
            byName = byName + instructors.associateBy { it.name }
        )
    }
}

/**
 * A professor teaching a course this term, summarized for the course card:
 * who they are, how they're rated, and how they've graded this course.
 */
data class CourseInstructorSummary(
    val name: String,
    val slug: String?,
    val sectionCount: Int,
    val openSeats: Int,
    val instructor: Instructor?,
    val grades: GradeDistribution?
)

/**
 * The distinct instructors across a course's sections, in section order,
 * joined with the directory and the course's per-professor grades.
 */
fun summarizeCourseInstructors(
    sections: List<Section>,
    directory: InstructorDirectory,
    grades: CourseGrades?
): List<CourseInstructorSummary> {
    data class Acc(val name: String, val slug: String?, var sections: Int = 0, var open: Int = 0)

    val byKey = LinkedHashMap<String, Acc>()
    sections.forEach { section ->
        section.instructorLinks.forEach { (name, slug) ->
            val acc = byKey.getOrPut(slug ?: "name:$name") { Acc(name, slug) }
            acc.sections += 1
            acc.open += section.openSeats.coerceAtLeast(0)
        }
    }
    return byKey.values.map { acc ->
        CourseInstructorSummary(
            name = acc.name,
            slug = acc.slug,
            sectionCount = acc.sections,
            openSeats = acc.open,
            instructor = directory.lookup(acc.name, acc.slug),
            grades = acc.slug?.let { grades?.byInstructorSlug?.get(it)?.distribution }
        )
    }
}
