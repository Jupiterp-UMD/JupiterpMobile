package com.jupiterp.jupiterpmobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.domain.model.GradeBucket
import com.jupiterp.jupiterpmobile.domain.model.GradeDistribution
import com.jupiterp.jupiterpmobile.domain.model.TermGpa
import com.jupiterp.jupiterpmobile.domain.model.Terms
import com.jupiterp.ui.theme.JupiterpTheme
import kotlin.math.roundToInt

/** "2.87" — GPAs are reported to two decimals, like a transcript. */
fun Float.toGpaString(): String {
    val hundredths = (this * 100).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

/** "14,989" without a locale-aware formatter. */
fun Int.withThousands(): String = toString().reversed().chunked(3).joinToString(",").reversed()

/**
 * Compact GPA marker: neutral container, the number in primary ink, and a
 * dot from the grade scale carrying the tier. Renders nothing when the
 * distribution is too thin to support a GPA.
 */
@Composable
fun GpaPill(
    distribution: GradeDistribution?,
    modifier: Modifier = Modifier,
    suffix: String = "GPA"
) {
    val gpa = distribution?.gpa
    if (distribution == null || gpa == null || !distribution.hasEnoughForGpa) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(JupiterpTheme.extendedColors.gpaColor(gpa))
            )
            Text(
                text = "${gpa.toGpaString()} $suffix",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * One stacked bar: A, B, C, D, F, W as shares of graded + withdrawn students,
 * so the segments sum to 100%. Segments are separated by a 2dp gap in the
 * surface color and only the outer ends are rounded.
 */
@Composable
fun GradeDistributionBar(
    distribution: GradeDistribution,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp
) {
    val colors = JupiterpTheme.extendedColors.gradeBuckets
    val shares = GradeBucket.entries.map { distribution.share(it) }
    val description = GradeBucket.entries.joinToString(", ") {
        "${it.label} ${distribution.percentLabel(it)} percent"
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Grade distribution: $description" }
    ) {
        val gap = 2.dp.toPx()
        val radius = CornerRadius(4.dp.toPx())
        val visible = shares.withIndex().filter { it.value > 0f }
        if (visible.isEmpty()) {
            drawRoundRect(color = colors[2].copy(alpha = 0.3f), cornerRadius = radius)
            return@Canvas
        }
        val usable = size.width - gap * (visible.size - 1)
        var x = 0f
        visible.forEachIndexed { position, (bucketIndex, share) ->
            // Keep tiny shares visible as a sliver rather than vanishing
            val width = (usable * share).coerceAtLeast(2.dp.toPx())
            val first = position == 0
            val last = position == visible.lastIndex
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = x, top = 0f, right = (x + width).coerceAtMost(size.width), bottom = size.height,
                        topLeftCornerRadius = if (first) radius else CornerRadius.Zero,
                        bottomLeftCornerRadius = if (first) radius else CornerRadius.Zero,
                        topRightCornerRadius = if (last) radius else CornerRadius.Zero,
                        bottomRightCornerRadius = if (last) radius else CornerRadius.Zero
                    )
                )
            }
            drawPath(path, colors[bucketIndex])
            x += width + gap
        }
    }
}

/** Swatch + letter + percent for each bucket, in secondary ink. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradeLegend(
    distribution: GradeDistribution,
    modifier: Modifier = Modifier
) {
    val colors = JupiterpTheme.extendedColors.gradeBuckets
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GradeBucket.entries.forEachIndexed { index, bucket ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors[index])
                )
                Text(
                    text = "${bucket.label} ${distribution.percentLabel(bucket)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

/**
 * Every letter with its count: the table view of the stacked bar. Percents
 * use the same graded + W denominator as the bar; "Other" (pass/fail,
 * incomplete, audit) is counted but not given a percent.
 */
@Composable
fun LetterBreakdown(
    distribution: GradeDistribution,
    modifier: Modifier = Modifier
) {
    val maxCount = GradeDistribution.LETTERS.maxOf { distribution.letters[it] ?: 0 }.coerceAtLeast(1)
    val colors = JupiterpTheme.extendedColors.gradeBuckets
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        GradeDistribution.LETTERS.forEach { letter ->
            val count = distribution.letters[letter] ?: 0
            if (letter == "Other" && count == 0) return@forEach
            val bucketIndex = when (letter.first()) {
                'A' -> 0; 'B' -> 1; 'C' -> 2; 'D' -> 3; 'F' -> 4; 'W' -> 5; else -> -1
            }
            val percent = if (letter == "Other" || distribution.barTotal == 0) null
            else (count.toFloat() / distribution.barTotal * 100)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = letter,
                    modifier = Modifier.width(40.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Box(Modifier.weight(1f).height(8.dp)) {
                    val fraction = count.toFloat() / maxCount
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .background(
                                    if (bucketIndex >= 0) colors[bucketIndex]
                                    else JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
                Text(
                    text = buildString {
                        append(count.withThousands())
                        percent?.let {
                            val rounded = it.roundToInt()
                            append(" · ")
                            append(if (rounded == 0 && count > 0) "<1" else rounded.toString())
                            append("%")
                        }
                    },
                    modifier = Modifier.widthIn(min = 76.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

/**
 * GPA headline, stacked bar, legend, and a caption, with an optional
 * per-letter breakdown behind a tap. Shows a "limited data" state instead of a
 * GPA when too few students received letter grades.
 */
@Composable
fun GradeSummaryPanel(
    distribution: GradeDistribution,
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = defaultGradeCaption(distribution)
) {
    var showLetters by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .clickable { showLetters = !showLetters }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                    val gpa = distribution.gpa
                    if (gpa != null && distribution.hasEnoughForGpa) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = gpa.toGpaString(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "  avg GPA",
                                style = MaterialTheme.typography.bodySmall,
                                color = JupiterpTheme.extendedColors.textSecondary,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Limited data",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Icon(
                    imageVector = if (showLetters) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (showLetters) "Hide letter grades" else "Show letter grades",
                    tint = JupiterpTheme.extendedColors.textSecondary
                )
            }
            GradeDistributionBar(distribution)
            GradeLegend(distribution)
            caption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
            AnimatedVisibility(
                visible = showLetters,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LetterBreakdown(distribution, Modifier.padding(top = 4.dp))
            }
        }
    }
}

fun defaultGradeCaption(distribution: GradeDistribution): String = buildString {
    append("${distribution.graded.withThousands()} graded students")
    distribution.termRange?.let { append(" · $it") }
    if (!distribution.hasEnoughForGpa) {
        append(" · too few for a reliable average")
    }
}

/**
 * GPA per term as a single orange line. Touch or drag to read any term;
 * without a touch, the latest term is shown. Hairline gridlines at the
 * half-points, first and last terms on the axis.
 */
@Composable
fun GpaTrendChart(
    points: List<TermGpa>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp
) {
    if (points.size < 2) return
    val orange = JupiterpTheme.extendedColors.orange
    val grid = JupiterpTheme.extendedColors.divider
    val muted = JupiterpTheme.extendedColors.textSecondary
    val surface = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted)

    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val shown = points[selected ?: points.lastIndex]

    // Pad the range so a flat line doesn't sit on an edge; stay within 0–4
    val minGpa = (points.minOf { it.gpa } - 0.2f).coerceAtLeast(0f)
    val maxGpa = (points.maxOf { it.gpa } + 0.2f).coerceAtMost(4f)
    val span = (maxGpa - minGpa).coerceAtLeast(0.4f)
    val gridValues = (0..8).map { it * 0.5f }.filter { it in minGpa..(minGpa + span) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = shown.gpa.toGpaString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "  ${Terms.label(shown.term)} · ${shown.graded.withThousands()} students",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    contentDescription = "GPA by term, from ${Terms.label(points.first().term)} " +
                        "(${points.first().gpa.toGpaString()}) to ${Terms.label(points.last().term)} " +
                        "(${points.last().gpa.toGpaString()})"
                }
                .pointerInput(points) {
                    detectTapGestures { selected = trendIndexAt(it.x, size.width, points.size, 32.dp.toPx(), 8.dp.toPx()) }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { selected = trendIndexAt(it.x, size.width, points.size, 32.dp.toPx(), 8.dp.toPx()) },
                        onHorizontalDrag = { change, _ ->
                            selected = trendIndexAt(change.position.x, size.width, points.size, 32.dp.toPx(), 8.dp.toPx())
                        }
                    )
                }
        ) {
            val left = 32.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val axisSpace = 18.dp.toPx()
            val top = 6.dp.toPx()
            val bottom = size.height - axisSpace
            fun xOf(i: Int) = left + (right - left) * i / (points.size - 1)
            fun yOf(gpa: Float) = bottom - (bottom - top) * ((gpa - minGpa) / span)

            gridValues.forEach { value ->
                val y = yOf(value)
                drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                val text = textMeasurer.measure(value.toGpaString().dropLast(1), labelStyle)
                drawText(text, topLeft = Offset(0f, y - text.size.height / 2f))
            }

            val path = Path().apply {
                points.forEachIndexed { i, p ->
                    if (i == 0) moveTo(xOf(i), yOf(p.gpa)) else lineTo(xOf(i), yOf(p.gpa))
                }
            }
            drawPath(path, orange, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Crosshair and marker for the shown term
            val shownIndex = selected ?: points.lastIndex
            val sx = xOf(shownIndex)
            val sy = yOf(points[shownIndex].gpa)
            if (selected != null) {
                drawLine(muted.copy(alpha = 0.5f), Offset(sx, top), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
            }
            drawCircle(surface, radius = 6.dp.toPx(), center = Offset(sx, sy))
            drawCircle(orange, radius = 4.dp.toPx(), center = Offset(sx, sy))

            val firstLabel = textMeasurer.measure(Terms.shortLabel(points.first().term), labelStyle)
            drawText(firstLabel, topLeft = Offset(left, size.height - firstLabel.size.height))
            val lastLabel = textMeasurer.measure(Terms.shortLabel(points.last().term), labelStyle)
            drawText(lastLabel, topLeft = Offset(right - lastLabel.size.width, size.height - lastLabel.size.height))
        }
    }
}

/** The trend point nearest [x], given the plot's left and right insets. */
private fun trendIndexAt(x: Float, width: Int, count: Int, leftInset: Float, rightInset: Float): Int {
    val step = (width - leftInset - rightInset) / (count - 1)
    return ((x - leftInset) / step).roundToInt().coerceIn(0, count - 1)
}

/** Read-only stars in half steps. */
@Composable
fun StarRating(
    rating: Float,
    modifier: Modifier = Modifier,
    starSize: Dp = 16.dp,
    color: Color = JupiterpTheme.extendedColors.orange
) {
    Row(
        modifier = modifier.semantics { contentDescription = "${rating.toHalfStepString()} out of 5 stars" },
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        val halves = (rating * 2).roundToInt()
        for (star in 1..5) {
            val icon = when {
                halves >= star * 2 -> Icons.Filled.Star
                halves == star * 2 - 1 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Outlined.StarOutline
            }
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(starSize))
        }
    }
}

/**
 * Tap or drag across the stars to rate in half steps: the left half of a
 * star gives x.5, the right half a whole star.
 */
@Composable
fun StarRatingInput(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 40.dp
) {
    val color = JupiterpTheme.extendedColors.orange
    fun ratingAt(x: Float, width: Float): Float {
        val raw = (x / width * 10).toInt() + 1 // halves, 1..10
        return (raw.coerceIn(2, 10)) / 2f
    }
    Row(
        modifier = modifier
            .semantics { contentDescription = "Rating ${rating.toHalfStepString()} out of 5" }
            .pointerInput(Unit) {
                detectTapGestures { onRatingChange(ratingAt(it.x, size.width.toFloat())) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onRatingChange(ratingAt(change.position.x.coerceIn(0f, size.width.toFloat()), size.width.toFloat()))
                }
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val halves = (rating * 2).roundToInt()
        for (star in 1..5) {
            val icon = when {
                halves >= star * 2 -> Icons.Filled.Star
                halves == star * 2 - 1 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Outlined.StarOutline
            }
            Icon(
                icon,
                contentDescription = null,
                tint = if (halves == 0) JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f) else color,
                modifier = Modifier.size(starSize)
            )
        }
    }
}

/** "4.5", "4.0" */
fun Float.toHalfStepString(): String {
    val halves = (this * 2).roundToInt()
    return "${halves / 2}.${if (halves % 2 == 1) 5 else 0}"
}

/** Short word for a half-step rating, shown under the input stars. */
fun ratingWord(rating: Float): String = when {
    rating <= 0f -> "Tap to rate"
    rating < 1.5f -> "Poor"
    rating < 2.5f -> "Below average"
    rating < 3.5f -> "Average"
    rating < 4.5f -> "Good"
    else -> "Excellent"
}
