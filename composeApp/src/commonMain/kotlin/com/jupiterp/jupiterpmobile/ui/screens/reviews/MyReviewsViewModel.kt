package com.jupiterp.jupiterpmobile.ui.screens.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiterp.jupiterpmobile.data.api.ApiException
import com.jupiterp.jupiterpmobile.data.repository.ReviewRepository
import com.jupiterp.jupiterpmobile.data.repository.VerifyOutcome
import com.jupiterp.jupiterpmobile.domain.model.ManagedReview
import com.jupiterp.jupiterpmobile.domain.model.StoredReviewKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the app knows about one stored key's review. */
sealed interface ManagedEntry {
    data object Loading : ManagedEntry
    data class Loaded(val review: ManagedReview) : ManagedEntry
    /** The server doesn't recognize the key (or it was mistyped). */
    data object Unrecognized : ManagedEntry
    data object Failed : ManagedEntry
}

sealed interface VerifyState {
    data object Verifying : VerifyState
    data class Verified(val manageKey: String, val message: String, val review: ManagedReview?) : VerifyState
    data class AlreadyVerified(val message: String) : VerifyState
    data class Failed(val message: String) : VerifyState
}

class MyReviewsViewModel(
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    val storedKeys: StateFlow<List<StoredReviewKey>> = reviewRepository.storedKeys
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _entries = MutableStateFlow<Map<String, ManagedEntry>>(emptyMap())
    val entries: StateFlow<Map<String, ManagedEntry>> = _entries.asStateFlow()

    private val _verify = MutableStateFlow<VerifyState?>(null)
    val verify: StateFlow<VerifyState?> = _verify.asStateFlow()

    private val _addKeyError = MutableStateFlow<String?>(null)
    val addKeyError: StateFlow<String?> = _addKeyError.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Look up the current status of every stored key. */
    fun refresh() {
        storedKeys.value.forEach { lookup(it.manageKey) }
    }

    private fun lookup(manageKey: String) {
        _entries.update { it + (manageKey to ManagedEntry.Loading) }
        viewModelScope.launch {
            val entry = reviewRepository.lookup(manageKey).fold(
                onSuccess = { ManagedEntry.Loaded(it) },
                onFailure = { error ->
                    if (error is ApiException && error.status == 401) ManagedEntry.Unrecognized
                    else ManagedEntry.Failed
                }
            )
            _entries.update { it + (manageKey to entry) }
        }
    }

    /** Add a key pasted from the email; only kept if the server recognizes it. */
    fun addKey(rawKey: String) {
        val key = rawKey.trim()
        if (key.isEmpty()) return
        _addKeyError.value = null
        viewModelScope.launch {
            reviewRepository.lookup(key)
                .onSuccess { review ->
                    reviewRepository.rememberKey(key, review)
                    _entries.update { it + (key to ManagedEntry.Loaded(review)) }
                }
                .onFailure { error ->
                    _addKeyError.value = if (error is ApiException && error.status == 401) {
                        "That key doesn't match a review. Check you copied all of it."
                    } else {
                        "Couldn't check that key. Try again in a moment."
                    }
                }
        }
    }

    fun clearAddKeyError() {
        _addKeyError.value = null
    }

    fun withdraw(manageKey: String, review: ManagedReview) {
        viewModelScope.launch {
            reviewRepository.withdraw(review, manageKey)
                .onSuccess {
                    _message.value = "Review withdrawn"
                    lookup(manageKey)
                }
                .onFailure { _message.value = "Couldn't withdraw the review. Try again in a moment." }
        }
    }

    fun forget(manageKey: String) {
        viewModelScope.launch {
            reviewRepository.forgetKey(manageKey)
            _entries.update { it - manageKey }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * Confirm an emailed link that opened the app. On first confirmation the
     * manage key is kept on this device straight away: it's shown once and
     * can't be recovered.
     */
    fun verify(token: String) {
        _verify.value = VerifyState.Verifying
        viewModelScope.launch {
            _verify.value = when (val outcome = reviewRepository.verify(token)) {
                is VerifyOutcome.Verified -> {
                    val review = reviewRepository.lookup(outcome.manageKey).getOrNull()
                    reviewRepository.rememberKey(outcome.manageKey, review)
                    review?.let { found -> _entries.update { it + (outcome.manageKey to ManagedEntry.Loaded(found)) } }
                    VerifyState.Verified(outcome.manageKey, outcome.message, review)
                }
                is VerifyOutcome.AlreadyVerified -> VerifyState.AlreadyVerified(outcome.message)
                is VerifyOutcome.Failed -> VerifyState.Failed(outcome.message)
            }
        }
    }

    fun dismissVerify() {
        _verify.value = null
    }
}
