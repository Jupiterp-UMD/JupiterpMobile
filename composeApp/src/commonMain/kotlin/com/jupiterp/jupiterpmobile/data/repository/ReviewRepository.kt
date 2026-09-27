package com.jupiterp.jupiterpmobile.data.repository

import com.jupiterp.jupiterpmobile.data.api.ApiException
import com.jupiterp.jupiterpmobile.data.api.JupiterpApiClient
import com.jupiterp.jupiterpmobile.data.model.ReportReviewRequest
import com.jupiterp.jupiterpmobile.data.model.SubmitReviewRequest
import com.jupiterp.jupiterpmobile.data.model.toDomain
import com.jupiterp.jupiterpmobile.data.storage.LocalStorage
import com.jupiterp.jupiterpmobile.domain.model.ManagedReview
import com.jupiterp.jupiterpmobile.domain.model.ReportReason
import com.jupiterp.jupiterpmobile.domain.model.ReviewDraft
import com.jupiterp.jupiterpmobile.domain.model.StoredReviewKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** What happened to a submission, phrased for the form. */
sealed class SubmitOutcome {
    /** Accepted; a confirmation link is on its way to the address. */
    data object VerificationSent : SubmitOutcome()
    /** The API's own message, written for people. */
    data class Rejected(val message: String, val rateLimited: Boolean) : SubmitOutcome()
    data object NetworkError : SubmitOutcome()
}

/** What confirming an emailed link produced. */
sealed class VerifyOutcome {
    /** First confirmation: the manage key is shown now and never again. */
    data class Verified(val manageKey: String, val message: String) : VerifyOutcome()
    /** A repeat visit (mail scanners prefetch links, people double-tap). */
    data class AlreadyVerified(val message: String) : VerifyOutcome()
    data class Failed(val message: String) : VerifyOutcome()
}

class ReviewRepository(
    private val apiClient: JupiterpApiClient,
    private val storage: LocalStorage
) {
    /** Manage keys kept on this device, newest first. */
    val storedKeys: Flow<List<StoredReviewKey>> = storage.getAppDataFlow()
        .map { it.reviewKeys }
        .distinctUntilChanged()

    suspend fun submit(
        instructorSlug: String,
        draft: ReviewDraft,
        captchaToken: String?
    ): SubmitOutcome {
        val request = SubmitReviewRequest(
            instructorSlug = instructorSlug,
            rating = draft.rating,
            email = draft.email.trim(),
            courseCode = draft.courseCode.trim().uppercase().ifEmpty { null },
            term = draft.term,
            expectedGrade = draft.expectedGrade.ifEmpty { null },
            title = draft.title.trim().ifEmpty { null },
            body = draft.body.trim().ifEmpty { null },
            captchaToken = captchaToken?.ifEmpty { null }
        )
        return apiClient.submitReview(request).fold(
            onSuccess = { SubmitOutcome.VerificationSent },
            onFailure = { error ->
                if (error is ApiException) {
                    SubmitOutcome.Rejected(
                        message = error.apiMessage?.asSentence() ?: "Something went wrong. Try again in a moment.",
                        rateLimited = error.isRateLimited
                    )
                } else {
                    SubmitOutcome.NetworkError
                }
            }
        )
    }

    suspend fun verify(token: String): VerifyOutcome =
        apiClient.verifyReview(token).fold(
            onSuccess = { response ->
                val key = response.manageKey
                when {
                    key != null -> VerifyOutcome.Verified(
                        key,
                        response.message ?: "Thanks. Your review is awaiting moderation."
                    )
                    else -> VerifyOutcome.AlreadyVerified(
                        response.message ?: "This review is already confirmed and awaiting moderation."
                    )
                }
            },
            onFailure = { error ->
                VerifyOutcome.Failed(
                    (error as? ApiException)?.apiMessage?.asSentence()
                        ?: "Couldn't reach Jupiterp. Check your connection and open the link again."
                )
            }
        )

    /** The review behind [manageKey], or a failure carrying the API's message. */
    suspend fun lookup(manageKey: String): Result<ManagedReview> =
        apiClient.getManagedReview(manageKey.trim()).map { it.toDomain() }

    suspend fun withdraw(review: ManagedReview, manageKey: String): Result<Unit> =
        apiClient.withdrawReview(review.id, manageKey.trim())

    suspend fun report(reviewId: String, reason: ReportReason, detail: String): Result<Unit> =
        apiClient.reportReview(reviewId, ReportReviewRequest(reason = reason.label, detail = detail.trim()))

    /** Keeps [manageKey] on this device, labelled from the review it controls. */
    suspend fun rememberKey(manageKey: String, review: ManagedReview?) {
        val key = manageKey.trim()
        if (key.isEmpty()) return
        val entry = StoredReviewKey(
            manageKey = key,
            instructorName = review?.instructorName.orEmpty(),
            instructorSlug = review?.instructorSlug.orEmpty(),
            courseCode = review?.courseCode,
            savedAt = Clock.System.now().toEpochMilliseconds()
        )
        storage.updateAppData { data ->
            data.copy(reviewKeys = listOf(entry) + data.reviewKeys.filterNot { it.manageKey == key })
        }
    }

    suspend fun forgetKey(manageKey: String) {
        storage.updateAppData { data ->
            data.copy(reviewKeys = data.reviewKeys.filterNot { it.manageKey == manageKey })
        }
    }
}

/**
 * The API writes its messages for people but in lowercase fragments
 * ("that link is not valid"); shown alone they read better as sentences.
 */
internal fun String.asSentence(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return trimmed
    val capitalized = trimmed.replaceFirstChar { it.uppercaseChar() }
    return if (capitalized.last() in ".!?") capitalized else "$capitalized."
}
