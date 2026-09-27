package com.jupiterp.jupiterpmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.domain.model.CourseInstructorSummary
import com.jupiterp.ui.theme.JupiterpTheme

/** Enough to open a professor's profile: the slug when known, and a name to show meanwhile. */
data class ProfessorRef(val name: String, val slug: String?)

/**
 * Opens a professor's profile from anywhere below the main screen. Null
 * where profiles aren't available, in which case instructor names render as
 * plain text.
 */
val LocalOpenProfessor = staticCompositionLocalOf<((ProfessorRef) -> Unit)?> { null }

/** Two-letter initials in an orange disc. */
@Composable
fun ProfessorAvatar(name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val initials = name.split(' ', '-')
        .filter { it.isNotBlank() && it.first().isLetter() }
        .let { parts -> listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull()) }
        .joinToString("") { it.first().uppercase() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(JupiterpTheme.extendedColors.orangeContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = JupiterpTheme.extendedColors.orange
        )
    }
}

/**
 * "Who's teaching" for a course card: one compact card per professor this
 * term, with their rating and how they've graded this course before. Tapping
 * one opens their profile. Sits above the section list so students can
 * compare professors before picking a section.
 */
@Composable
fun CourseInstructorStrip(
    summaries: List<CourseInstructorSummary>,
    modifier: Modifier = Modifier
) {
    if (summaries.isEmpty()) return
    val openProfessor = LocalOpenProfessor.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (summaries.size == 1) "Instructor" else "Instructors",
            style = MaterialTheme.typography.labelMedium,
            color = JupiterpTheme.extendedColors.textSecondary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(summaries, key = { it.slug ?: "name:${it.name}" }) { summary ->
                InstructorSummaryCard(
                    summary = summary,
                    onClick = openProfessor?.let { open -> { open(ProfessorRef(summary.name, summary.slug)) } }
                )
            }
        }
    }
}

@Composable
private fun InstructorSummaryCard(
    summary: CourseInstructorSummary,
    onClick: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .width(212.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfessorAvatar(summary.name, size = 28.dp)
                Text(
                    text = summary.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (onClick != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View profile",
                        modifier = Modifier.size(18.dp),
                        tint = JupiterpTheme.extendedColors.textSecondary
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RatingChip(rating = summary.instructor?.rating)
                GpaPill(distribution = summary.grades, suffix = "here")
                if (summary.instructor?.rating == null && summary.grades?.hasEnoughForGpa != true) {
                    Text(
                        text = "No ratings or grades yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                }
            }
            Text(
                text = buildString {
                    append("${summary.sectionCount} section${if (summary.sectionCount != 1) "s" else ""}")
                    append(" · ")
                    append(if (summary.openSeats > 0) "${summary.openSeats} open seats" else "full")
                },
                style = MaterialTheme.typography.labelSmall,
                color = JupiterpTheme.extendedColors.textSecondary
            )
        }
    }
}
