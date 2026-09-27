package com.jupiterp.jupiterpmobile.domain.model

import kotlinx.serialization.Serializable

/**
 * Reviews are pre-moderated: nothing submitted is public until a moderator
 * approves it. The flow is
 *
 *   submit  → an email with a confirmation link goes to a UMD address
 *   verify  → the link moves the review to the moderation queue and hands
 *             back a one-time "manage key"
 *   publish → a moderator (or the automated triage) approves it
 *
 * The manage key is the only thing tying a person to their review — the
 * server deliberately can't link it back to anyone — and it's what withdraws
 * the review later. There is no edit: to change a review, withdraw it and
 * write another.
 */
data class Review(
    val id: String,
    val courseCode: String?,
    val term: Int?,
    /** 1–5 in half steps. */
    val rating: Float,
    val expectedGrade: String?,
    val title: String?,
    val body: String?,
    /** ISO-8601 timestamp. */
    val submittedAt: String,
    val edited: Boolean
) {
    /** "Mar 2026" from the ISO timestamp, without a date library. */
    val submittedLabel: String
        get() = formatIsoMonthYear(submittedAt) ?: ""
}

enum class ReviewStatus(val label: String, val explanation: String) {
    Unverified("Awaiting email confirmation", "Open the link we emailed you to confirm it's yours."),
    Pending("In moderation", "A moderator will review it before it's published."),
    Escalated("In moderation", "A moderator will review it before it's published."),
    Approved("Published", "It's visible on the professor's page."),
    Rejected("Not published", "It didn't meet the review policy."),
    Withdrawn("Withdrawn", "You took it down. Its text has been deleted."),
    Unknown("Unknown", "");

    companion object {
        fun fromApi(value: String): ReviewStatus = when (value) {
            "unverified" -> Unverified
            "pending" -> Pending
            "escalated" -> Escalated
            "approved" -> Approved
            "rejected" -> Rejected
            "withdrawn" -> Withdrawn
            else -> Unknown
        }
    }
}

/** The review a manage key controls. */
data class ManagedReview(
    val id: String,
    val instructorName: String,
    val instructorSlug: String,
    val courseCode: String?,
    val term: Int?,
    val rating: Float,
    val title: String?,
    val body: String?,
    val status: ReviewStatus,
    val submittedAt: String,
    val withdrawable: Boolean
)

/**
 * A manage key kept on this device so the reviewer can check on or withdraw
 * their review later without digging up the email. Stored only locally.
 */
@Serializable
data class StoredReviewKey(
    val manageKey: String,
    val instructorName: String = "",
    val instructorSlug: String = "",
    val courseCode: String? = null,
    val savedAt: Long = 0
)

/** Everything the review form collects. */
data class ReviewDraft(
    val rating: Float = 0f,
    val courseCode: String = "",
    val term: Int? = null,
    val expectedGrade: String = "",
    val title: String = "",
    val body: String = "",
    val email: String = "",
    val agreedToPolicy: Boolean = false
)

/**
 * The server's validation rules, applied before sending so the form can say
 * what's wrong next to the field instead of after a round trip. The server
 * checks all of these again.
 */
object ReviewRules {
    const val TITLE_MAX = 120
    const val BODY_MAX = 5000

    val ALLOWED_EMAIL_DOMAINS = listOf("terpmail.umd.edu", "umd.edu")

    /** Every grade the API accepts for `expected_grade`. */
    val EXPECTED_GRADES = listOf("A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "D-", "F", "W", "Other")

    private val courseCodeRegex = Regex("^[A-Z]{4}\\d{3}[A-Z]?$")
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun isValidRating(rating: Float): Boolean =
        rating in 1f..5f && (rating * 2) == (rating * 2).toInt().toFloat()

    fun isValidCourseCode(code: String): Boolean = courseCodeRegex.matches(code)

    fun emailError(email: String): String? {
        val trimmed = email.trim().lowercase()
        if (trimmed.isEmpty()) return null
        if (!emailRegex.matches(trimmed)) return "That doesn't look like an email address"
        val domain = trimmed.substringAfterLast('@')
        return if (domain in ALLOWED_EMAIL_DOMAINS) null
        else "Use your terpmail.umd.edu or umd.edu address"
    }

    /** Characters as the server counts them (code points, not UTF-16 units). */
    fun length(text: String): Int = text.codePointCount()

    /** The first thing stopping a submit, or null when the draft can go. */
    fun blockingIssue(draft: ReviewDraft): String? = when {
        !isValidRating(draft.rating) -> "Pick a rating"
        draft.courseCode.isNotEmpty() && !isValidCourseCode(draft.courseCode) -> "Course code should look like CMSC132"
        length(draft.title.trim()) > TITLE_MAX -> "Title is limited to $TITLE_MAX characters"
        length(draft.body.trim()) > BODY_MAX -> "Review is limited to $BODY_MAX characters"
        draft.email.isBlank() -> "Enter your UMD email to confirm the review"
        emailError(draft.email) != null -> emailError(draft.email)
        !draft.agreedToPolicy -> "Agree to the review policy to submit"
        else -> null
    }

    private fun String.codePointCount(): Int {
        var count = 0
        var i = 0
        while (i < length) {
            i += if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
            count++
        }
        return count
    }
}

/**
 * Why someone might report a review. The server takes free text; these are
 * the cases the moderation rules act on, so a moderator can triage quickly.
 */
enum class ReportReason(val label: String) {
    Personal("Personal attack or harassment"),
    Discrimination("Hateful or discriminatory"),
    PrivateInfo("Shares private information"),
    OffTopic("Not about teaching or the course"),
    FalseInfo("False or misleading"),
    Spam("Spam or not a real review"),
    Other("Something else")
}

/**
 * Normalizes a name the way the API's `name_norm` column does, so a
 * `nameSearch` for "O'Brien" or "José" finds "o brien" and "jose".
 */
fun normalizeNameForSearch(raw: String): String {
    val folded = buildString {
        for (ch in raw.lowercase()) {
            append(ACCENT_FOLDS[ch] ?: ch)
        }
    }
    return folded
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

private val ACCENT_FOLDS: Map<Char, String> = buildMap {
    fun fold(target: String, chars: String) = chars.forEach { put(it, target) }
    fold("a", "àáâãäåāăą")
    fold("c", "çćĉċč")
    fold("d", "ďđ")
    fold("e", "èéêëēĕėęě")
    fold("g", "ĝğġģ")
    fold("h", "ĥħ")
    fold("i", "ìíîïĩīĭįı")
    fold("j", "ĵ")
    fold("k", "ķ")
    fold("l", "ĺļľŀł")
    fold("n", "ñńņňŉ")
    fold("o", "òóôõöøōŏő")
    fold("r", "ŕŗř")
    fold("s", "śŝşš")
    fold("t", "ţťŧ")
    fold("u", "ùúûüũūŭůűų")
    fold("w", "ŵ")
    fold("y", "ýÿŷ")
    fold("z", "źżž")
    put('ß', "ss")
    put('æ', "ae")
    put('œ', "oe")
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

internal fun formatIsoMonthYear(iso: String): String? {
    if (iso.length < 7) return null
    val year = iso.substring(0, 4).toIntOrNull() ?: return null
    val month = iso.substring(5, 7).toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return "${MONTHS[month - 1]} $year"
}
