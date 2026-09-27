package com.jupiterp.jupiterpmobile.ui.screens.reviews

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.domain.model.ManagedReview
import com.jupiterp.jupiterpmobile.domain.model.ReviewStatus
import com.jupiterp.jupiterpmobile.domain.model.StoredReviewKey
import com.jupiterp.jupiterpmobile.domain.model.Terms
import com.jupiterp.jupiterpmobile.ui.components.ProfessorRef
import com.jupiterp.jupiterpmobile.ui.components.StarRating
import com.jupiterp.ui.theme.JupiterpTheme

/**
 * Reviews written from this device, by their manage keys. Lets the reviewer
 * see where each one is in moderation and withdraw it. There's no edit — the
 * API deliberately has none — so the copy points to withdraw-and-rewrite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReviewsSheet(
    storedKeys: List<StoredReviewKey>,
    entries: Map<String, ManagedEntry>,
    addKeyError: String?,
    onAddKey: (String) -> Unit,
    onClearAddKeyError: () -> Unit,
    onWithdraw: (String, ManagedReview) -> Unit,
    onForget: (String) -> Unit,
    onOpenProfessor: (ProfessorRef) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingWithdraw by remember { mutableStateOf<Pair<String, ManagedReview>?>(null) }
    var keyInput by remember { mutableStateOf("") }

    pendingWithdraw?.let { (key, review) ->
        AlertDialog(
            onDismissRequest = { pendingWithdraw = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Withdraw this review?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Its text is deleted and can't be restored. Reviews can't be edited, so to change " +
                        "what you wrote, withdraw it and write a new one."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onWithdraw(key, review)
                    pendingWithdraw = null
                }) { Text("Withdraw", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingWithdraw = null }) { Text("Cancel") } }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("My reviews", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Reviews you've confirmed on this phone. The manage key is the only link between you " +
                            "and a review, and it's kept only here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                }
            }

            if (storedKeys.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.RateReview,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
                        )
                        Text("No reviews on this phone", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Write one from any professor's profile, or add the manage key from a confirmation email below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JupiterpTheme.extendedColors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(storedKeys, key = { it.manageKey }) { stored ->
                    ManagedReviewCard(
                        stored = stored,
                        entry = entries[stored.manageKey],
                        onWithdraw = { review -> pendingWithdraw = stored.manageKey to review },
                        onForget = { onForget(stored.manageKey) },
                        onOpenProfessor = onOpenProfessor
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text("Add a manage key", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "It's in the email you got after confirming a review.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = {
                                keyInput = it.trim()
                                onClearAddKeyError()
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Paste manage key") },
                            singleLine = true,
                            isError = addKeyError != null,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JupiterpTheme.extendedColors.orange,
                                cursorColor = JupiterpTheme.extendedColors.orange
                            )
                        )
                        Button(
                            onClick = {
                                onAddKey(keyInput)
                                keyInput = ""
                            },
                            enabled = keyInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JupiterpTheme.extendedColors.orange, contentColor = Color.White)
                        ) { Text("Add") }
                    }
                    addKeyError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagedReviewCard(
    stored: StoredReviewKey,
    entry: ManagedEntry?,
    onWithdraw: (ManagedReview) -> Unit,
    onForget: () -> Unit,
    onOpenProfessor: (ProfessorRef) -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val review = (entry as? ManagedEntry.Loaded)?.review
            val name = review?.instructorName?.ifBlank { null } ?: stored.instructorName.ifBlank { "Review" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val details = listOfNotNull(
                        review?.courseCode ?: stored.courseCode,
                        review?.term?.let { Terms.label(it) }
                    )
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = JupiterpTheme.extendedColors.textSecondary
                        )
                    }
                }
                review?.let { StarRating(it.rating, starSize = 14.dp) }
            }

            when (entry) {
                null, ManagedEntry.Loading -> Text(
                    "Checking status…",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
                ManagedEntry.Failed -> Text(
                    "Couldn't check this review's status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
                ManagedEntry.Unrecognized -> Text(
                    "This key no longer matches a review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                is ManagedEntry.Loaded -> {
                    StatusLine(entry.review.status)
                    entry.review.title?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (review != null && review.withdrawable) {
                    TextButton(onClick = { onWithdraw(review) }) {
                        Text("Withdraw", color = MaterialTheme.colorScheme.error)
                    }
                }
                val slug = review?.instructorSlug?.ifBlank { null } ?: stored.instructorSlug.ifBlank { null }
                if (slug != null) {
                    TextButton(onClick = { onOpenProfessor(ProfessorRef(name, slug)) }) {
                        Text("Profile", color = JupiterpTheme.extendedColors.orange)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(stored.manageKey)) }) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy manage key",
                        modifier = Modifier.size(18.dp),
                        tint = JupiterpTheme.extendedColors.textSecondary
                    )
                }
                TextButton(onClick = onForget) {
                    Text("Remove", color = JupiterpTheme.extendedColors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(status: ReviewStatus) {
    val color = when (status) {
        ReviewStatus.Approved -> JupiterpTheme.extendedColors.success
        ReviewStatus.Rejected -> MaterialTheme.colorScheme.error
        else -> JupiterpTheme.extendedColors.textSecondary
    }
    Column {
        Text(status.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color)
        if (status.explanation.isNotEmpty()) {
            Text(status.explanation, style = MaterialTheme.typography.bodySmall, color = JupiterpTheme.extendedColors.textSecondary)
        }
    }
}

/** Shown when a confirmation link from the review email opens the app. */
@Composable
fun VerifyReviewDialog(
    state: VerifyState,
    onDismiss: () -> Unit,
    onOpenMyReviews: () -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { if (state !is VerifyState.Verifying) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        icon = {
            when (state) {
                is VerifyState.Verified, is VerifyState.AlreadyVerified ->
                    Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = JupiterpTheme.extendedColors.success)
                is VerifyState.Failed ->
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                VerifyState.Verifying -> CircularProgressIndicator(Modifier.size(28.dp), color = JupiterpTheme.extendedColors.orange)
            }
        },
        title = {
            Text(
                when (state) {
                    VerifyState.Verifying -> "Confirming your review…"
                    is VerifyState.Verified -> "Review confirmed"
                    is VerifyState.AlreadyVerified -> "Already confirmed"
                    is VerifyState.Failed -> "Couldn't confirm"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            when (state) {
                VerifyState.Verifying -> Text("This only takes a moment.")
                is VerifyState.AlreadyVerified -> Text(state.message)
                is VerifyState.Failed -> Text(state.message)
                is VerifyState.Verified -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${state.message} It's published once a moderator approves it.")
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.manageKey,
                                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { clipboard.setText(AnnotatedString(state.manageKey)) }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy manage key")
                            }
                        }
                    }
                    Text(
                        "This manage key is the only way to withdraw your review. It's saved on this phone " +
                            "under My reviews, and we emailed you a copy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            if (state !is VerifyState.Verifying) {
                TextButton(onClick = onDismiss) { Text("Done", color = JupiterpTheme.extendedColors.orange) }
            }
        },
        dismissButton = {
            if (state is VerifyState.Verified) {
                TextButton(onClick = onOpenMyReviews) { Text("My reviews") }
            }
        }
    )
}
