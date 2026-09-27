package com.jupiterp.jupiterpmobile.domain.model

import kotlin.math.roundToInt

/**
 * Grade data comes from UMD's Office of the Registrar (released under the
 * Maryland Public Information Act) and is aggregated by the Jupiterp API.
 * Fall and Spring terms only.
 *
 * Two denominators, and they are not interchangeable:
 *
 *   GPA          over `graded`     — letter grades only; a W is not in a
 *                                    transcript GPA, so it isn't here either
 *   Bar percent  over `graded + W` — so the bars sum to 100% and the
 *                                    withdrawal rate is visible on its own
 *
 * Neither uses `total`: before Fall 2017 the registrar's total includes
 * students whose outcome was never categorized.
 *
 * The GPA is always taken from the API, never recomputed here, so the app and
 * the site can't show two different numbers for the same course.
 */
enum class GradeBucket(val label: String) {
    A("A"), B("B"), C("C"), D("D"), F("F"), W("W")
}

data class GradeDistribution(
    /** Students per letter, keyed "A+" … "F", "W", "Other", best first. */
    val letters: Map<String, Int>,
    val graded: Int,
    val total: Int,
    val gpa: Float?,
    val firstTerm: Int?,
    val lastTerm: Int?,
    val sectionCount: Int,
    val termCount: Int?
) {
    val buckets: Map<GradeBucket, Int> = GradeBucket.entries.associateWith { bucket ->
        BUCKET_LETTERS.getValue(bucket).sumOf { letters[it] ?: 0 }
    }

    /** Denominator for the bars: letter grades plus withdrawals. */
    val barTotal: Int get() = graded + (letters["W"] ?: 0)

    /**
     * Below [MIN_GRADED_FOR_GPA] graded students a GPA is noise. A
     * three-student section with a 4.0 says nothing about a course, so it's
     * shown as limited data rather than a confident-looking number.
     */
    val hasEnoughForGpa: Boolean get() = gpa != null && graded >= MIN_GRADED_FOR_GPA

    val termRange: String? get() = Terms.rangeLabel(firstTerm, lastTerm)

    /** Share of [barTotal] in [bucket], 0–1. */
    fun share(bucket: GradeBucket): Float =
        if (barTotal == 0) 0f else (buckets[bucket] ?: 0).toFloat() / barTotal

    /** Whole-number percent for display; "<1" for nonzero counts that round to 0. */
    fun percentLabel(bucket: GradeBucket): String {
        if (barTotal == 0) return "0"
        val count = buckets[bucket] ?: 0
        val percent = (count.toFloat() / barTotal * 100).roundToInt()
        return if (percent == 0 && count > 0) "<1" else percent.toString()
    }

    companion object {
        const val MIN_GRADED_FOR_GPA = 20

        val LETTERS = listOf("A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "D-", "F", "W", "Other")

        private val BUCKET_LETTERS = mapOf(
            GradeBucket.A to listOf("A+", "A", "A-"),
            GradeBucket.B to listOf("B+", "B", "B-"),
            GradeBucket.C to listOf("C+", "C", "C-"),
            GradeBucket.D to listOf("D+", "D", "D-"),
            GradeBucket.F to listOf("F"),
            GradeBucket.W to listOf("W")
        )
    }
}

/** One professor's record in one course. */
data class InstructorCourseGrades(
    val instructorSlug: String,
    val instructorName: String,
    val distribution: GradeDistribution
)

/**
 * Grade data for a course: the course-wide distribution plus one per
 * professor. The course-wide figure includes sections with no attributed
 * instructor (about a quarter of historical rows), so it isn't the sum of the
 * per-professor rows.
 */
data class CourseGrades(
    val courseCode: String,
    val course: GradeDistribution,
    val byInstructorSlug: Map<String, InstructorCourseGrades>
)

/** Grade state for one course as the UI sees it. */
sealed class CourseGradesState {
    data object Loading : CourseGradesState()
    data class Loaded(val grades: CourseGrades) : CourseGradesState()
    /** No grade records exist: a new course, or one never offered in Fall/Spring. */
    data object None : CourseGradesState()
    data object Error : CourseGradesState()
}

/**
 * The GPA a student in [section] can expect, going by past grades: the mean
 * of its instructors' GPAs in this course, counting only records with enough
 * graded students to be meaningful. When none of the section's instructors
 * has such a record (new to the course, or unresolved), the course-wide GPA
 * stands in. Null when the course itself has too little grade data.
 */
fun expectedSectionGpa(section: Section, grades: CourseGrades?): Float? {
    if (grades == null) return null
    val instructorGpas = section.instructorLinks.mapNotNull { (_, slug) ->
        slug?.let { grades.byInstructorSlug[it]?.distribution }
            ?.takeIf { it.hasEnoughForGpa }
            ?.gpa
    }
    if (instructorGpas.isNotEmpty()) return instructorGpas.average().toFloat()
    return grades.course.takeIf { it.hasEnoughForGpa }?.gpa
}

/** A course a professor has taught, with how they graded it. */
data class ProfessorCourseRecord(
    val courseCode: String,
    val distribution: GradeDistribution
)

/** One point of a professor's GPA-over-time trend. */
data class TermGpa(
    val term: Int,
    val gpa: Float,
    val graded: Int
)
