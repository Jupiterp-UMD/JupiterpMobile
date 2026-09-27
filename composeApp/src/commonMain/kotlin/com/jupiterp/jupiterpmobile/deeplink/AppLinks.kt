package com.jupiterp.jupiterpmobile.deeplink

import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import io.ktor.http.Url

/**
 * jupiterp.com links the app builds and acts on. Incoming shared-schedule
 * links are decoded by [ShareLink]; this handles the rest, and builds the
 * outgoing share link.
 *
 * The review emails link to the site (`/review/verify?token=…` and
 * `/review/withdraw`). When one of those opens the app instead of a browser,
 * it has to be handled here: dropping it would leave the review stuck
 * unconfirmed with nothing telling the reviewer why.
 */
sealed class AppLink {
    /** Confirm the review behind an emailed verification token. */
    data class VerifyReview(val token: String) : AppLink()

    /** Open the reviewer's own reviews to check on or withdraw one. */
    data object ManageReviews : AppLink()

    /** A professor's profile, by Jupiterp slug. */
    data class Professor(val slug: String) : AppLink()
}

object AppLinks {
    private val HOSTS = setOf("jupiterp.com", "www.jupiterp.com")
    private val SLUG = Regex("^[a-z0-9][a-z0-9-]*$")

    fun professorUrl(slug: String): String = "https://jupiterp.com/professor/$slug"

    /** Section code of a course added without picking a section. */
    private const val NO_SECTION = "---"

    /**
     * A link to [selections] in the site's `?s=` format, which the site and
     * this app both open. Courses added without a section can't be expressed
     * in a link, so they're left out and reported in [ScheduleShare.skippedCourses].
     * Null when nothing in the schedule can be shared.
     */
    fun scheduleShare(selections: List<ScheduleSelection>): ScheduleShare? {
        val (withSection, withoutSection) = selections.partition { it.section.sectionCode != NO_SECTION }
        if (withSection.isEmpty()) return null
        val token = ShareLink.encodeSchedule(
            withSection.map { CourseSectionPair(it.course.courseCode, it.section.sectionCode) }
        )
        return ScheduleShare(
            url = "https://jupiterp.com/?s=$token",
            skippedCourses = withoutSection.map { it.course.courseCode }.distinct()
        )
    }

    /** Returns null for anything that isn't one of the links above. */
    fun parse(url: String): AppLink? {
        val parsed = runCatching { Url(url.trim()) }.getOrNull() ?: return null
        if (parsed.host.lowercase() !in HOSTS) return null

        val segments = parsed.encodedPath.split('/').filter { it.isNotEmpty() }
        return when {
            segments == listOf("review", "verify") ->
                parsed.parameters["token"]?.takeIf { it.isNotBlank() }?.let { AppLink.VerifyReview(it) }

            segments == listOf("review", "withdraw") -> AppLink.ManageReviews

            segments.size == 2 && segments[0] == "professor" ->
                segments[1].lowercase().takeIf { SLUG.matches(it) }?.let { AppLink.Professor(it) }

            else -> null
        }
    }
}

data class ScheduleShare(
    val url: String,
    /** Courses in the schedule that aren't in the link. */
    val skippedCourses: List<String>
)
