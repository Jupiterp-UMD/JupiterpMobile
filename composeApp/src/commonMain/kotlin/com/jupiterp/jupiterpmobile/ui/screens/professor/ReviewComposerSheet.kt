package com.jupiterp.jupiterpmobile.ui.screens.professor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.domain.model.ReportReason
import com.jupiterp.jupiterpmobile.domain.model.Review
import com.jupiterp.jupiterpmobile.domain.model.ReviewDraft
import com.jupiterp.jupiterpmobile.domain.model.ReviewRules
import com.jupiterp.jupiterpmobile.domain.model.Terms
import com.jupiterp.jupiterpmobile.ui.components.ReviewConfig
import com.jupiterp.jupiterpmobile.ui.components.StarRatingInput
import com.jupiterp.jupiterpmobile.ui.components.TurnstileWidget
import com.jupiterp.jupiterpmobile.ui.components.ratingWord
import com.jupiterp.jupiterpmobile.ui.components.toHalfStepString
import com.jupiterp.ui.theme.JupiterpTheme

/**
 * Write a review. Everything but the rating and email is optional; what the
 * form asks for mirrors what the API accepts, and its rules are checked
 * before sending so problems show up next to the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewComposerSheet(
    state: ComposerState,
    darkTheme: Boolean,
    onUpdate: ((ReviewDraft) -> ReviewDraft) -> Unit,
    onCaptchaToken: (String?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        if (state.sentTo != null) {
            SentConfirmation(email = state.sentTo, professorName = state.professorName, onDone = onDismiss)
        } else {
            ComposerForm(state, darkTheme, onUpdate, onCaptchaToken, onSubmit, onDismiss)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerForm(
    state: ComposerState,
    darkTheme: Boolean,
    onUpdate: ((ReviewDraft) -> ReviewDraft) -> Unit,
    onCaptchaToken: (String?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val draft = state.draft
    val uriHandler = LocalUriHandler.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = JupiterpTheme.extendedColors.orange,
        cursorColor = JupiterpTheme.extendedColors.orange,
        focusedLabelColor = JupiterpTheme.extendedColors.orange
    )
    var customCourse by remember { mutableStateOf(draft.courseCode.isNotEmpty() && draft.courseCode !in state.courseOptions) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Review ${state.professorName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Your review is anonymous. A moderator checks it before it's published.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Close; your draft is kept")
            }
        }

        // Rating
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            StarRatingInput(rating = draft.rating, onRatingChange = { value -> onUpdate { it.copy(rating = value) } })
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (draft.rating > 0f) "${draft.rating.toHalfStepString()} · ${ratingWord(draft.rating)}" else ratingWord(0f),
                style = MaterialTheme.typography.labelLarge,
                color = if (draft.rating > 0f) MaterialTheme.colorScheme.onSurface else JupiterpTheme.extendedColors.textSecondary
            )
        }

        // Course
        FieldGroup("Course", optional = true) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.courseOptions.forEach { code ->
                    ChoiceChip(
                        label = code,
                        selected = !customCourse && draft.courseCode == code,
                        onClick = {
                            customCourse = false
                            onUpdate { it.copy(courseCode = if (it.courseCode == code) "" else code) }
                        }
                    )
                }
                ChoiceChip(
                    label = if (state.courseOptions.isEmpty()) "Enter a course" else "Other",
                    selected = customCourse,
                    onClick = {
                        customCourse = !customCourse
                        onUpdate { it.copy(courseCode = "") }
                    }
                )
            }
            if (customCourse) {
                val invalid = draft.courseCode.isNotEmpty() && !ReviewRules.isValidCourseCode(draft.courseCode)
                OutlinedTextField(
                    value = draft.courseCode,
                    onValueChange = { value ->
                        onUpdate { it.copy(courseCode = value.uppercase().filter { c -> c.isLetterOrDigit() }.take(8)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. CMSC132") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) ({ Text("Course code should look like CMSC132") }) else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )
            }
        }

        // Term
        FieldGroup("When you took it", optional = true) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.termOptions) { term ->
                    ChoiceChip(
                        label = Terms.label(term),
                        selected = draft.term == term,
                        onClick = { onUpdate { it.copy(term = if (it.term == term) null else term) } }
                    )
                }
            }
        }

        // Expected grade
        FieldGroup("Grade you got or expect", optional = true) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReviewRules.EXPECTED_GRADES) { grade ->
                    ChoiceChip(
                        label = grade,
                        selected = draft.expectedGrade == grade,
                        onClick = { onUpdate { it.copy(expectedGrade = if (it.expectedGrade == grade) "" else grade) } }
                    )
                }
            }
        }

        // Title and body
        FieldGroup("Your review", optional = true) {
            val titleLength = ReviewRules.length(draft.title)
            OutlinedTextField(
                value = draft.title,
                onValueChange = { value -> onUpdate { it.copy(title = value) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Headline") },
                singleLine = true,
                isError = titleLength > ReviewRules.TITLE_MAX,
                supportingText = { CharacterCount(titleLength, ReviewRules.TITLE_MAX) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )
            val bodyLength = ReviewRules.length(draft.body)
            OutlinedTextField(
                value = draft.body,
                onValueChange = { value -> onUpdate { it.copy(body = value) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                placeholder = { Text("What was the class actually like? Workload, grading, whether the lectures helped.") },
                isError = bodyLength > ReviewRules.BODY_MAX,
                supportingText = { CharacterCount(bodyLength, ReviewRules.BODY_MAX) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )
        }

        // Email
        FieldGroup("UMD email") {
            val emailError = ReviewRules.emailError(draft.email)
            OutlinedTextField(
                value = draft.email,
                onValueChange = { value -> onUpdate { it.copy(email = value.trim()) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("you@terpmail.umd.edu") },
                singleLine = true,
                isError = emailError != null,
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                supportingText = {
                    Text(
                        emailError ?: "We email you a link to confirm the review. Your address is stored only " +
                            "as a one-way hash — it's never shown, and never shared with the professor."
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )
        }

        // Policy
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Be honest and specific about the teaching and the course",
                    "No personal attacks, and nothing that identifies anyone",
                    "One review per professor and course; you can withdraw it later"
                ).forEach { rule ->
                    Text("•  $rule", style = MaterialTheme.typography.bodySmall, color = JupiterpTheme.extendedColors.textSecondary)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = draft.agreedToPolicy,
                            role = Role.Checkbox,
                            onClick = { onUpdate { it.copy(agreedToPolicy = !it.agreedToPolicy) } }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = draft.agreedToPolicy,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = JupiterpTheme.extendedColors.orange)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("I agree to the review policy", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { uriHandler.openUri(ReviewConfig.REVIEW_POLICY_URL) }) {
                        Text("Read", color = JupiterpTheme.extendedColors.orange)
                    }
                }
            }
        }

        if (ReviewConfig.captchaRequired) {
            TurnstileWidget(
                siteKey = ReviewConfig.TURNSTILE_SITE_KEY,
                darkTheme = darkTheme,
                generation = state.captchaGeneration,
                onToken = onCaptchaToken
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            val blocking = ReviewRules.blockingIssue(draft)
            Button(
                onClick = onSubmit,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JupiterpTheme.extendedColors.orange,
                    contentColor = Color.White
                )
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Submit review", fontWeight = FontWeight.SemiBold)
                }
            }
            if (blocking != null && state.error == null) {
                Text(
                    text = blocking,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SentConfirmation(email: String, professorName: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Outlined.MarkEmailRead,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = JupiterpTheme.extendedColors.orange
        )
        Text("Check your inbox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = "We sent a confirmation link to $email. Your review of $professorName goes to a " +
                "moderator once you open it, and is published after it's approved.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "The link expires in 48 hours. Confirming also gives you a manage key for " +
                "withdrawing the review later — if you open the link on this phone, it's saved " +
                "under Settings → My reviews.",
            style = MaterialTheme.typography.bodySmall,
            color = JupiterpTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JupiterpTheme.extendedColors.orange, contentColor = Color.White)
        ) {
            Text("Done", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FieldGroup(label: String, optional: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (optional) {
                Text(
                    "  optional",
                    style = MaterialTheme.typography.labelSmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
        }
        content()
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) JupiterpTheme.extendedColors.orange else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun CharacterCount(length: Int, max: Int) {
    Text(
        text = "$length / $max",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End,
        color = if (length > max) MaterialTheme.colorScheme.error else JupiterpTheme.extendedColors.textSecondary
    )
}

/**
 * Report a published review. The reasons are the cases the moderation rules
 * act on; an optional note gives the moderator context.
 */
@Composable
fun ReportReviewDialog(
    state: ReportState,
    onSubmit: (Review, ReportReason, String) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is ReportState.Sent) {
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Thanks for reporting", fontWeight = FontWeight.Bold) },
            text = { Text("A moderator will take a look. Reviews that break the policy are taken down.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Done", color = JupiterpTheme.extendedColors.orange) }
            }
        )
        return
    }

    val review = when (state) {
        is ReportState.Choosing -> state.review
        is ReportState.Sending -> state.review
        is ReportState.Failed -> state.review
        ReportState.Sent -> return
    }
    var reason by remember(review.id) { mutableStateOf<ReportReason?>(null) }
    var detail by remember(review.id) { mutableStateOf("") }
    val sending = state is ReportState.Sending

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Report review", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "What's wrong with it?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
                Spacer(Modifier.height(6.dp))
                ReportReason.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = reason == option, role = Role.RadioButton, onClick = { reason = option })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = reason == option,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = JupiterpTheme.extendedColors.orange)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it.take(1000) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Anything a moderator should know (optional)") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )
                if (state is ReportState.Failed) {
                    Spacer(Modifier.height(6.dp))
                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { reason?.let { onSubmit(review, it, detail) } },
                enabled = reason != null && !sending
            ) {
                Text(if (sending) "Sending…" else "Send report", color = if (reason != null) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
