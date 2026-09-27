package com.jupiterp.jupiterpmobile.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.CourseGradesState
import com.jupiterp.jupiterpmobile.domain.model.InstructorDirectory
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.domain.model.summarizeCourseInstructors
import kotlinx.coroutines.delay
import com.jupiterp.ui.theme.JupiterpTheme

/**
 * Course card with expandable sections
 */
@Composable
fun CourseCard(
    course: Course,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    isSectionSelected: (String) -> Boolean,
    isCourseSelected: Boolean,
    instructorDirectory: InstructorDirectory,
    gradesState: CourseGradesState?,
    onRequestGrades: (retryError: Boolean) -> Unit,
    onSectionToggle: (Section) -> Unit,
    onAddCourseWithoutSection: () -> Unit,
    modifier: Modifier = Modifier,
    hasConflict: (Section) -> Boolean = { false }
) {
    // Fetch grades once the card has been on screen briefly; a card scrolled
    // past quickly leaves composition first and cancels this
    LaunchedEffect(course.courseCode) {
        delay(300)
        onRequestGrades(false)
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "chevron_rotation"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column {
            // Course header (always visible)
            CourseHeader(
                course = course,
                gradesState = gradesState,
                isExpanded = isExpanded,
                rotationAngle = rotationAngle,
                onClick = onExpandToggle
            )

            // Expandable sections
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(150)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(100))
            ) {
                CourseSections(
                    course = course,
                    isSectionSelected = isSectionSelected,
                    isCourseSelected = isCourseSelected,
                    instructorDirectory = instructorDirectory,
                    gradesState = gradesState,
                    onRetryGrades = { onRequestGrades(true) },
                    onSectionToggle = onSectionToggle,
                    onAddCourseWithoutSection = onAddCourseWithoutSection,
                    hasConflict = hasConflict
                )
            }
        }
    }
}

/**
 * Course header with basic info
 */
@Composable
private fun CourseHeader(
    course: Course,
    gradesState: CourseGradesState?,
    isExpanded: Boolean,
    rotationAngle: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Course code and name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = course.courseCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JupiterpTheme.extendedColors.orange
                )

                CreditsBadge(credits = course.credits)

                GpaPill(distribution = (gradesState as? CourseGradesState.Loaded)?.grades?.course)
            }

            Text(
                text = course.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // GenEd badges
            if (!course.genEds.isNullOrEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    course.genEds.take(4).forEach { genEd ->
                        GenEdBadge(genEd = genEd)
                    }

                    if (course.genEds.size > 4) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "+${course.genEds.size - 4}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = JupiterpTheme.extendedColors.textSecondary
                            )
                        }
                    }
                }
            }

            // Section count
            course.sections?.let { sections ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = JupiterpTheme.extendedColors.textSecondary
                    )
                    Text(
                        text = "${sections.size} section${if (sections.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )

                    // Available seats summary
                    val availableSections = sections.count { it.openSeats > 0 }
                    if (availableSections > 0) {
                        Text(
                            text = "• $availableSections open",
                            style = MaterialTheme.typography.bodySmall,
                            color = JupiterpTheme.extendedColors.success
                        )
                    } else {
                        Text(
                            text = "• All full",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Expand/collapse indicator
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier
                .rotate(rotationAngle)
                .padding(8.dp),
            tint = JupiterpTheme.extendedColors.textSecondary
        )
    }
}

/**
 * Course sections list
 */
@Composable
private fun CourseSections(
    course: Course,
    isSectionSelected: (String) -> Boolean,
    isCourseSelected: Boolean,
    instructorDirectory: InstructorDirectory,
    gradesState: CourseGradesState?,
    onRetryGrades: () -> Unit,
    onSectionToggle: (Section) -> Unit,
    onAddCourseWithoutSection: () -> Unit,
    modifier: Modifier = Modifier,
    hasConflict: (Section) -> Boolean = { false }
) {
    Column(
        modifier = modifier.padding(
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Course description (if available)
        course.description?.let { description ->
            var descExpanded by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { descExpanded = !descExpanded },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.textSecondary,
                        maxLines = if (descExpanded) Int.MAX_VALUE else 3,
                        overflow = if (descExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
                    )
                    if (!descExpanded) {
                        Text(
                            text = "Show more",
                            style = MaterialTheme.typography.labelSmall,
                            color = JupiterpTheme.extendedColors.orange,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Prerequisites (if available)
        course.conditions?.let { conditions ->
            if (conditions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = JupiterpTheme.extendedColors.warning.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = JupiterpTheme.extendedColors.warning
                        )
                        Text(
                            text = conditions.joinToString("; "),
                            style = MaterialTheme.typography.bodySmall,
                            color = JupiterpTheme.extendedColors.textSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        CourseGradesBlock(gradesState = gradesState, onRetry = onRetryGrades)

        // Who's teaching this term, to compare professors before picking a section
        val summaries = remember(course.sections, instructorDirectory, gradesState) {
            summarizeCourseInstructors(
                sections = course.sections.orEmpty(),
                directory = instructorDirectory,
                grades = (gradesState as? CourseGradesState.Loaded)?.grades
            )
        }
        CourseInstructorStrip(summaries = summaries)

        // Divider
        HorizontalDivider(
            color = JupiterpTheme.extendedColors.divider,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Section rows or "No sections" message with add button
        if (course.sections.isNullOrEmpty()) {
            // No sections available - show add course button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "No sections available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JupiterpTheme.extendedColors.textSecondary
                        )
                        Text(
                            text = "Add to track this course",
                            style = MaterialTheme.typography.bodySmall,
                            color = JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.7f)
                        )
                    }

                    Button(
                        onClick = onAddCourseWithoutSection,
                        enabled = !isCourseSelected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JupiterpTheme.extendedColors.orange,
                            disabledContainerColor = JupiterpTheme.extendedColors.success.copy(alpha = 0.3f)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isCourseSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Added", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        } else {
            course.sections.forEach { section ->
                val isSelected = isSectionSelected(section.sectionCode)
                // Co-taught sections: show the first instructor that has a rating
                val instructorRating = instructorDirectory.ratingFor(section)

                SectionRow(
                    section = section,
                    isSelected = isSelected,
                    instructorRating = instructorRating,
                    onToggle = { onSectionToggle(section) },
                    hasConflict = hasConflict(section)
                )
            }
        }
    }
}

/**
 * The course's grade history, or a quiet line explaining its absence. Grade
 * data is supplementary, so failures are one line with a retry, never a
 * blocking error.
 */
@Composable
private fun CourseGradesBlock(
    gradesState: CourseGradesState?,
    onRetry: () -> Unit
) {
    when (gradesState) {
        is CourseGradesState.Loaded -> GradeSummaryPanel(
            distribution = gradesState.grades.course,
            title = "Past grades, all instructors"
        )
        CourseGradesState.None -> GradesNote("No grade history yet — new, or not offered in a fall or spring term")
        CourseGradesState.Error -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GradesNote("Couldn't load grade history", Modifier.weight(1f))
            TextButton(onClick = onRetry) {
                Text("Retry", color = JupiterpTheme.extendedColors.orange)
            }
        }
        CourseGradesState.Loading, null -> GradesNote("Loading grade history…")
    }
}

@Composable
private fun GradesNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.BarChart,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = JupiterpTheme.extendedColors.textSecondary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = JupiterpTheme.extendedColors.textSecondary
        )
    }
}

/**
 * Compact course card for schedule sidebar
 */
@Composable
fun CompactCourseCard(
    course: Course,
    section: Section,
    colorIndex: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheduleColor = JupiterpTheme.extendedColors.scheduleColors[colorIndex % JupiterpTheme.extendedColors.scheduleColors.size]

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = scheduleColor.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = course.courseCode,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    // Show "No section" for placeholder sections
                    val sectionDisplay = if (section.sectionCode == "---") "No section" else section.sectionCode
                    Text(
                        text = sectionDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.sectionCodes
                    )
                }

                Text(
                    text = course.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Meeting times (or "No meetings" for placeholder sections)
                val timedMeeting = section.meetings.firstNotNullOfOrNull { meeting ->
                    when (meeting) {
                        is ClassMeeting.InPerson -> meeting.classtime
                        is ClassMeeting.OnlineSync -> meeting.classtime
                        else -> null
                    }
                }
                if (timedMeeting != null) {
                    Text(
                        text = "${timedMeeting.days} ${timedMeeting.timeRange}",
                        style = MaterialTheme.typography.labelSmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                } else if (section.meetings.isEmpty()) {
                    Text(
                        text = "No scheduled meetings",
                        style = MaterialTheme.typography.labelSmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(18.dp),
                    tint = JupiterpTheme.extendedColors.textSecondary
                )
            }
        }
    }
}