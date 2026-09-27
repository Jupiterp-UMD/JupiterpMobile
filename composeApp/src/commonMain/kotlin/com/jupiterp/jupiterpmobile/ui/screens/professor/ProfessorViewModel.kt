package com.jupiterp.jupiterpmobile.ui.screens.professor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiterp.jupiterpmobile.currentDateInt
import com.jupiterp.jupiterpmobile.data.repository.ProfessorRepository
import com.jupiterp.jupiterpmobile.data.repository.ReviewRepository
import com.jupiterp.jupiterpmobile.data.repository.SubmitOutcome
import com.jupiterp.jupiterpmobile.domain.model.GradeDistribution
import com.jupiterp.jupiterpmobile.domain.model.Instructor
import com.jupiterp.jupiterpmobile.domain.model.ProfessorCourseRecord
import com.jupiterp.jupiterpmobile.domain.model.ReportReason
import com.jupiterp.jupiterpmobile.domain.model.Review
import com.jupiterp.jupiterpmobile.domain.model.ReviewDraft
import com.jupiterp.jupiterpmobile.domain.model.ReviewRules
import com.jupiterp.jupiterpmobile.domain.model.TermGpa
import com.jupiterp.jupiterpmobile.domain.model.Terms
import com.jupiterp.jupiterpmobile.ui.components.ProfessorRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A value that loads independently of the rest of the profile. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data object Failed : Loadable<Nothing>

    fun valueOrNull(): T? = (this as? Ready)?.value
}

data class ReviewsState(
    val items: List<Review> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = true,
    val failed: Boolean = false
) {
    val canLoadMore: Boolean get() = !loading && total != null && items.size < total
}

data class ProfileState(
    val ref: ProfessorRef,
    /** Resolved slug; null while resolving or when there's no record. */
    val slug: String?,
    val instructor: Loadable<Instructor?>,
    val overall: Loadable<GradeDistribution?> = Loadable.Loading,
    val courses: Loadable<List<ProfessorCourseRecord>> = Loadable.Loading,
    val trend: Loadable<List<TermGpa>> = Loadable.Loading,
    val currentCourses: Loadable<List<String>> = Loadable.Loading,
    val reviews: ReviewsState = ReviewsState()
) {
    val displayName: String get() = instructor.valueOrNull()?.name ?: ref.name

    /** No Jupiterp record exists for this name (e.g. a TA listed only on Testudo). */
    val notFound: Boolean get() = instructor is Loadable.Ready && instructor.value == null
}

data class ComposerState(
    val slug: String,
    val professorName: String,
    val draft: ReviewDraft,
    /** Courses to offer as one-tap choices: this term's first, then taught before. */
    val courseOptions: List<String>,
    val termOptions: List<Int>,
    val captchaToken: String? = null,
    /** Bumped to make the captcha issue a fresh token; tokens are single-use. */
    val captchaGeneration: Int = 0,
    val submitting: Boolean = false,
    val error: String? = null,
    /** Set once the API accepted the review: the address the link went to. */
    val sentTo: String? = null
)

sealed interface ReportState {
    data class Choosing(val review: Review) : ReportState
    data class Sending(val review: Review) : ReportState
    data object Sent : ReportState
    data class Failed(val review: Review, val message: String) : ReportState
}

class ProfessorViewModel(
    private val professorRepository: ProfessorRepository,
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _profile = MutableStateFlow<ProfileState?>(null)
    val profile: StateFlow<ProfileState?> = _profile.asStateFlow()

    private val _composer = MutableStateFlow<ComposerState?>(null)
    val composer: StateFlow<ComposerState?> = _composer.asStateFlow()

    private val _report = MutableStateFlow<ReportState?>(null)
    val report: StateFlow<ReportState?> = _report.asStateFlow()

    // Unsent drafts by slug, so closing the sheet doesn't cost someone a long review
    private val drafts = mutableMapOf<String, ReviewDraft>()

    private var loadJob: Job? = null
    private var reviewsJob: Job? = null

    fun open(ref: ProfessorRef) {
        val current = _profile.value
        if (current != null && ref.slug != null && current.slug == ref.slug) return

        loadJob?.cancel()
        reviewsJob?.cancel()
        _profile.value = ProfileState(ref = ref, slug = ref.slug, instructor = Loadable.Loading)
        loadJob = viewModelScope.launch { load(ref) }
    }

    fun close() {
        loadJob?.cancel()
        reviewsJob?.cancel()
        _profile.value = null
    }

    fun retry() {
        _profile.value?.ref?.let { ref ->
            _profile.value = null
            open(ref)
        }
    }

    private suspend fun load(ref: ProfessorRef) {
        // Resolve a slug when the section didn't carry one
        val instructor = if (ref.slug != null) {
            professorRepository.getProfessor(ref.slug)
        } else {
            professorRepository.searchProfessors(ref.name, limit = 5).map { matches ->
                matches.firstOrNull { it.name.equals(ref.name, ignoreCase = true) }
            }
        }.getOrElse {
            updateProfile { it.copy(instructor = Loadable.Failed) }
            return
        }

        val slug = instructor?.slug ?: ref.slug
        updateProfile { it.copy(slug = slug, instructor = Loadable.Ready(instructor)) }
        if (slug == null || instructor == null) return

        loadReviews(slug, reset = true)

        // Scoped to the load job, so opening another professor cancels these too
        coroutineScope {
            val overall = async { professorRepository.getOverallGrades(slug) }
            val courses = async { professorRepository.getCourseRecords(slug) }
            val trend = async { professorRepository.getTermTrend(slug) }
            val current = async { professorRepository.getCurrentCourses(slug) }
            updateProfile { it.copy(overall = overall.await().toLoadable()) }
            updateProfile { it.copy(courses = courses.await().toLoadable()) }
            updateProfile { it.copy(trend = trend.await().toLoadable()) }
            updateProfile { it.copy(currentCourses = current.await().toLoadable()) }
        }
    }

    fun loadMoreReviews() {
        val slug = _profile.value?.slug ?: return
        loadReviews(slug, reset = false)
    }

    private fun loadReviews(slug: String, reset: Boolean) {
        val existing = _profile.value?.reviews ?: return
        val offset = if (reset) 0 else existing.items.size
        reviewsJob?.cancel()
        updateProfile { it.copy(reviews = it.reviews.copy(loading = true, failed = false)) }
        reviewsJob = viewModelScope.launch {
            professorRepository.getReviews(slug, offset = offset)
                .onSuccess { page ->
                    updateProfile { state ->
                        val items = if (reset) page.data else state.reviews.items + page.data
                        state.copy(
                            reviews = ReviewsState(
                                items = items.distinctBy { it.id },
                                total = page.total ?: items.size,
                                loading = false
                            )
                        )
                    }
                }
                .onFailure {
                    updateProfile { it.copy(reviews = it.reviews.copy(loading = false, failed = true)) }
                }
        }
    }

    private inline fun updateProfile(transform: (ProfileState) -> ProfileState) {
        _profile.update { current -> current?.let(transform) }
    }

    private fun <T> Result<T>.toLoadable(): Loadable<T> =
        fold(onSuccess = { Loadable.Ready(it) }, onFailure = { Loadable.Failed })

    /* ============================== reporting ============================== */

    fun startReport(review: Review) {
        _report.value = ReportState.Choosing(review)
    }

    fun submitReport(review: Review, reason: ReportReason, detail: String) {
        _report.value = ReportState.Sending(review)
        viewModelScope.launch {
            reviewRepository.report(review.id, reason, detail)
                .onSuccess { _report.value = ReportState.Sent }
                .onFailure { _report.value = ReportState.Failed(review, "Couldn't send the report. Try again in a moment.") }
        }
    }

    fun dismissReport() {
        _report.value = null
    }

    /* =============================== composer ============================== */

    fun startReview() {
        val profile = _profile.value ?: return
        val slug = profile.slug ?: return
        val current = profile.currentCourses.valueOrNull().orEmpty()
        val past = profile.courses.valueOrNull().orEmpty().map { it.courseCode }
        val terms = Terms.startedTerms(currentDateInt(), count = 8)
        val draft = drafts[slug] ?: ReviewDraft(term = terms.firstOrNull())
        _composer.value = ComposerState(
            slug = slug,
            professorName = profile.displayName,
            draft = draft,
            courseOptions = (current + past).distinct().take(10),
            termOptions = terms
        )
    }

    fun updateDraft(transform: (ReviewDraft) -> ReviewDraft) {
        _composer.update { state ->
            state?.let {
                val draft = transform(it.draft)
                drafts[it.slug] = draft
                it.copy(draft = draft, error = null)
            }
        }
    }

    fun onCaptchaToken(token: String?) {
        _composer.update { it?.copy(captchaToken = token) }
    }

    fun submitReview(captchaRequired: Boolean) {
        val state = _composer.value ?: return
        if (state.submitting) return
        ReviewRules.blockingIssue(state.draft)?.let { issue ->
            _composer.value = state.copy(error = issue)
            return
        }
        if (captchaRequired && state.captchaToken.isNullOrEmpty()) {
            _composer.value = state.copy(error = "Complete the verification check first")
            return
        }
        _composer.value = state.copy(submitting = true, error = null)
        viewModelScope.launch {
            val outcome = reviewRepository.submit(state.slug, state.draft, state.captchaToken)
            _composer.update { current ->
                current ?: return@update null
                when (outcome) {
                    SubmitOutcome.VerificationSent -> {
                        drafts.remove(current.slug)
                        current.copy(submitting = false, sentTo = current.draft.email.trim())
                    }
                    // A captcha token is single-use, so any failure needs a fresh one
                    is SubmitOutcome.Rejected -> current.copy(
                        submitting = false,
                        error = outcome.message,
                        captchaToken = null,
                        captchaGeneration = current.captchaGeneration + 1
                    )
                    SubmitOutcome.NetworkError -> current.copy(
                        submitting = false,
                        error = "Couldn't reach Jupiterp. Check your connection and try again.",
                        captchaToken = null,
                        captchaGeneration = current.captchaGeneration + 1
                    )
                }
            }
        }
    }

    fun closeComposer() {
        _composer.value = null
    }
}
