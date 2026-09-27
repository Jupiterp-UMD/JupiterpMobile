package com.jupiterp.jupiterpmobile.ui.screens.professor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.domain.model.GradeDistribution
import com.jupiterp.jupiterpmobile.domain.model.Instructor
import com.jupiterp.jupiterpmobile.domain.model.ProfessorCourseRecord
import com.jupiterp.jupiterpmobile.domain.model.Review
import com.jupiterp.jupiterpmobile.domain.model.Terms
import com.jupiterp.jupiterpmobile.toOneDecimalString
import com.jupiterp.jupiterpmobile.ui.components.GpaPill
import com.jupiterp.jupiterpmobile.ui.components.GpaTrendChart
import com.jupiterp.jupiterpmobile.ui.components.GradeDistributionBar
import com.jupiterp.jupiterpmobile.ui.components.GradeSummaryPanel
import com.jupiterp.jupiterpmobile.ui.components.ProfessorAvatar
import com.jupiterp.jupiterpmobile.ui.components.ReviewConfig
import com.jupiterp.jupiterpmobile.ui.components.SolarSystemLoader
import com.jupiterp.jupiterpmobile.ui.components.StarRating
import com.jupiterp.jupiterpmobile.ui.components.toHalfStepString
import com.jupiterp.jupiterpmobile.ui.components.withThousands
import com.jupiterp.ui.theme.JupiterpTheme

private enum class ProfileTab { Grades, Reviews }

/**
 * A professor's profile: rating, how they grade, what they teach, and what
 * students say. Opened from course cards, search suggestions, schedule
 * blocks, and jupiterp.com/professor links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessorSheet(
    state: ProfileState,
    servedTerm: Int?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onWriteReview: () -> Unit,
    onLoadMoreReviews: () -> Unit,
    onReportReview: (Review) -> Unit,
    onSearchCourse: (String) -> Unit
) {
    var tab by rememberSaveable(state.slug) { mutableStateOf(ProfileTab.Grades) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { ProfileHeader(state, servedTerm) }

            when {
                state.instructor is Loadable.Loading -> item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        SolarSystemLoader(size = 96.dp)
                    }
                }
                state.instructor is Loadable.Failed -> item {
                    ProfileMessage(
                        title = "Couldn't load this profile",
                        body = "Check your connection and try again.",
                        actionLabel = "Retry",
                        onAction = onRetry
                    )
                }
                state.notFound -> item {
                    ProfileMessage(
                        title = "No profile yet",
                        body = "Jupiterp doesn't have a record for ${state.ref.name} yet. " +
                            "New instructors and TAs appear once they're matched."
                    )
                }
                else -> {
                    val instructor = state.instructor.valueOrNull() ?: return@LazyColumn
                    item { RatingCard(instructor) }
                    item {
                        Button(
                            onClick = onWriteReview,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JupiterpTheme.extendedColors.orange,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Outlined.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Write a review", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    item {
                        val reviewCount = state.reviews.total
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ProfileTab.entries.forEachIndexed { index, entry ->
                                SegmentedButton(
                                    selected = tab == entry,
                                    onClick = { tab = entry },
                                    shape = SegmentedButtonDefaults.itemShape(index, ProfileTab.entries.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = JupiterpTheme.extendedColors.orangeContainer,
                                        activeContentColor = JupiterpTheme.extendedColors.orange
                                    ),
                                    icon = {}
                                ) {
                                    Text(
                                        when (entry) {
                                            ProfileTab.Grades -> "Grades"
                                            ProfileTab.Reviews -> if (reviewCount != null && reviewCount > 0) "Reviews · $reviewCount" else "Reviews"
                                        }
                                    )
                                }
                            }
                        }
                    }
                    when (tab) {
                        ProfileTab.Grades -> gradesTab(state, servedTerm, onSearchCourse)
                        ProfileTab.Reviews -> reviewsTab(
                            state = state,
                            onWriteReview = onWriteReview,
                            onLoadMore = onLoadMoreReviews,
                            onReport = onReportReview
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(state: ProfileState, servedTerm: Int?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProfessorAvatar(state.displayName, size = 56.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            val current = state.currentCourses.valueOrNull()
            val termName = servedTerm?.let { Terms.label(it) } ?: "this term"
            val status = when {
                current == null -> null
                current.isEmpty() -> "Not teaching in $termName"
                else -> "Teaching in $termName"
            }
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current.isNullOrEmpty()) JupiterpTheme.extendedColors.textSecondary
                    else JupiterpTheme.extendedColors.success
                )
            }
        }
    }
}

/**
 * The blended rating and where it comes from. The number mixes PlanetTerp's
 * archived ratings with reviews written here, so the card says so rather than
 * presenting one opaque average.
 */
@Composable
private fun RatingCard(instructor: Instructor) {
    var showExplainer by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val rating = instructor.rating
                if (rating != null) {
                    Text(
                        text = rating.toOneDecimalString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        StarRating(rating, starSize = 18.dp)
                        Text(
                            text = "out of 5",
                            style = MaterialTheme.typography.labelSmall,
                            color = JupiterpTheme.extendedColors.textSecondary
                        )
                    }
                } else {
                    Column {
                        Text("Not rated yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Not enough ratings to show a score",
                            style = MaterialTheme.typography.bodySmall,
                            color = JupiterpTheme.extendedColors.textSecondary
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showExplainer = !showExplainer }) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "How this rating works",
                        tint = JupiterpTheme.extendedColors.textSecondary
                    )
                }
            }
            ratingSources(instructor)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = JupiterpTheme.extendedColors.textSecondary)
            }
            AnimatedVisibility(showExplainer) {
                Text(
                    text = "This score blends PlanetTerp's archived ratings with reviews written on Jupiterp. " +
                        "Newer reviews count for more, and professors with few ratings are pulled toward " +
                        "the campus average until more come in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

private fun ratingSources(instructor: Instructor): String? {
    val parts = buildList {
        instructor.ptReviewCount?.takeIf { it > 0 }?.let { add("$it PlanetTerp") }
        instructor.jupiterpReviewCount.takeIf { it > 0 }?.let { add("$it Jupiterp") }
    }
    if (parts.isEmpty()) return null
    val total = (instructor.ptReviewCount ?: 0) + instructor.jupiterpReviewCount
    return "From ${parts.joinToString(" and ")} review${if (total != 1) "s" else ""}"
}

private fun LazyListScope.gradesTab(
    state: ProfileState,
    servedTerm: Int?,
    onSearchCourse: (String) -> Unit
) {
    val current = state.currentCourses.valueOrNull().orEmpty()
    if (current.isNotEmpty()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Teaching in ${servedTerm?.let { Terms.label(it) } ?: "this term"}")
                CourseChips(current, onSearchCourse)
            }
        }
    }

    when (val overall = state.overall) {
        Loadable.Loading -> item { InlineLoading("Loading grades…") }
        Loadable.Failed -> item { InlineNote("Couldn't load grade history.") }
        is Loadable.Ready -> {
            val distribution = overall.value
            if (distribution == null) {
                item {
                    InlineNote("No grade records yet. Grades are released after each fall and spring term.")
                }
            } else {
                item {
                    val courseCount = state.courses.valueOrNull()?.size
                    GradeSummaryPanel(
                        distribution = distribution,
                        title = "Across all their courses",
                        caption = overallCaption(distribution, courseCount)
                    )
                }
            }
        }
    }

    state.trend.valueOrNull()?.takeIf { it.size >= 2 }?.let { trend ->
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("GPA over time")
                GpaTrendChart(trend)
                Text(
                    text = "Touch the chart to see any term",
                    style = MaterialTheme.typography.labelSmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
        }
    }

    state.courses.valueOrNull()?.takeIf { it.isNotEmpty() }?.let { courses ->
        item { SectionTitle("Courses taught") }
        items(courses, key = { "course-${it.courseCode}" }) { record ->
            CourseRecordRow(record, isCurrent = record.courseCode in current, onClick = { onSearchCourse(record.courseCode) })
        }
    }

    item {
        Text(
            text = "Grades from UMD's Office of the Registrar, fall and spring terms. " +
                "Averages exclude withdrawals; the W bar shows them separately.",
            style = MaterialTheme.typography.labelSmall,
            color = JupiterpTheme.extendedColors.textSecondary
        )
    }
}

private fun overallCaption(distribution: GradeDistribution, courseCount: Int?): String = buildString {
    append("${distribution.graded.withThousands()} graded students")
    courseCount?.takeIf { it > 0 }?.let { append(" in $it course${if (it != 1) "s" else ""}") }
    distribution.termRange?.let { append(" · $it") }
}

@Composable
private fun CourseRecordRow(record: ProfessorCourseRecord, isCurrent: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = record.courseCode,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = JupiterpTheme.extendedColors.orange
                )
                GpaPill(record.distribution)
                if (isCurrent) {
                    Text(
                        text = "This term",
                        style = MaterialTheme.typography.labelSmall,
                        color = JupiterpTheme.extendedColors.success
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "Search ${record.courseCode}",
                    modifier = Modifier.size(18.dp),
                    tint = JupiterpTheme.extendedColors.textSecondary
                )
            }
            GradeDistributionBar(record.distribution, height = 8.dp)
            Text(
                text = buildString {
                    append("${record.distribution.graded.withThousands()} students")
                    record.distribution.termRange?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = JupiterpTheme.extendedColors.textSecondary
            )
        }
    }
}

private fun LazyListScope.reviewsTab(
    state: ProfileState,
    onWriteReview: () -> Unit,
    onLoadMore: () -> Unit,
    onReport: (Review) -> Unit
) {
    val reviews = state.reviews
    when {
        reviews.items.isEmpty() && reviews.loading -> item { InlineLoading("Loading reviews…") }
        reviews.items.isEmpty() && reviews.failed -> item { InlineNote("Couldn't load reviews.") }
        reviews.items.isEmpty() -> item {
            ProfileMessage(
                title = "No reviews yet",
                body = "Be the first to share what ${state.displayName.substringBefore(' ')}'s class is like.",
                actionLabel = "Write the first review",
                onAction = onWriteReview
            )
        }
        else -> {
            items(reviews.items, key = { "review-${it.id}" }) { review ->
                ReviewCard(review, onReport = { onReport(review) })
            }
            if (reviews.canLoadMore || (reviews.loading && reviews.items.isNotEmpty())) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (reviews.loading) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = JupiterpTheme.extendedColors.orange, strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = onLoadMore) {
                                Text("Show more reviews", color = JupiterpTheme.extendedColors.orange)
                            }
                        }
                    }
                }
            }
        }
    }
    item { ModerationFootnote() }
}

@Composable
private fun ModerationFootnote() {
    val uriHandler = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Every review is checked by a moderator before it's published.",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = JupiterpTheme.extendedColors.textSecondary
        )
        TextButton(onClick = { uriHandler.openUri(ReviewConfig.REVIEW_POLICY_URL) }) {
            Text("Policy", style = MaterialTheme.typography.labelMedium, color = JupiterpTheme.extendedColors.orange)
        }
    }
}

/** One published review. Long reviews collapse to six lines. */
@Composable
fun ReviewCard(review: Review, onReport: (() -> Unit)?) {
    var expanded by remember(review.id) { mutableStateOf(false) }
    var overflow by remember(review.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarRating(review.rating, starSize = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = review.rating.toHalfStepString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = review.submittedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
                if (onReport != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "More options",
                                modifier = Modifier.size(18.dp),
                                tint = JupiterpTheme.extendedColors.textSecondary
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Report review") },
                                onClick = {
                                    menuOpen = false
                                    onReport()
                                }
                            )
                        }
                    }
                }
            }

            val tags = listOfNotNull(
                review.courseCode,
                review.term?.let { Terms.label(it) },
                review.expectedGrade?.let { "Expected $it" }
            )
            if (tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 10.dp)) {
                    tags.forEachIndexed { index, tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (index == 0 && review.courseCode != null) JupiterpTheme.extendedColors.orangeContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (index == 0 && review.courseCode != null) JupiterpTheme.extendedColors.orange
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(tag, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                review.title?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                review.body?.let { body ->
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow }
                    )
                    if (overflow || expanded) {
                        Text(
                            text = if (expanded) "Show less" else "Read more",
                            style = MaterialTheme.typography.labelMedium,
                            color = JupiterpTheme.extendedColors.orange,
                            modifier = Modifier.clickable { expanded = !expanded }
                        )
                    }
                }
                if (review.title == null && review.body == null) {
                    Text(
                        text = "Rating only",
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseChips(codes: List<String>, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        codes.forEach { code ->
            Surface(
                modifier = Modifier.clickable { onClick(code) },
                shape = RoundedCornerShape(10.dp),
                color = JupiterpTheme.extendedColors.orangeContainer,
                contentColor = JupiterpTheme.extendedColors.orange
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun InlineLoading(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), color = JupiterpTheme.extendedColors.orange, strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = JupiterpTheme.extendedColors.textSecondary)
    }
}

@Composable
private fun InlineNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = JupiterpTheme.extendedColors.textSecondary)
}

@Composable
private fun ProfileMessage(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Outlined.PersonSearch,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
        )
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = JupiterpTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = JupiterpTheme.extendedColors.orange, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
