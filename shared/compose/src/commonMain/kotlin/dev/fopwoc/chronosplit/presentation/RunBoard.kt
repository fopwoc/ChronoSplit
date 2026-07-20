package dev.fopwoc.chronosplit.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fopwoc.chronosplit.model.LayoutAccuracy
import dev.fopwoc.chronosplit.model.LayoutColor
import dev.fopwoc.chronosplit.model.LayoutDefinition
import dev.fopwoc.chronosplit.model.LayoutGeneral
import dev.fopwoc.chronosplit.model.RunBoardModel
import dev.fopwoc.chronosplit.model.RunBoardColumnValueType
import dev.fopwoc.chronosplit.model.RunBoardPreviousSegmentModel
import dev.fopwoc.chronosplit.model.RunBoardSegmentModel
import dev.fopwoc.chronosplit.model.RunBoardSemanticColor
import dev.fopwoc.chronosplit.model.RunStatus
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
fun RunBoard(
    model: RunBoardModel,
    modifier: Modifier = Modifier,
    onSegmentClick: (() -> Unit)? = null,
) {
    val layout = model.layout

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(layout.general.background.toComposeColor()),
    ) {
        if (layout.title.enabled) BoardHeader(model)
        if (layout.splits.showColumnLabels) SplitColumnHeader(layout)
        SplitsViewport(model, Modifier.weight(1f), onSegmentClick)
        RunTimer(model)
        if (layout.previousSegment.enabled) {
            model.previousSegment?.let { previous ->
                PreviousSegment(previous, layout)
            }
        }
    }
}

@Composable
private fun BoardHeader(model: RunBoardModel) {
    val general = model.layout.general
    val gameTitle = model.gameName
        ?.takeIf { model.layout.title.showGameName && it.isNotBlank() }
        ?: model.title
    val categoryTitle = model.categoryName
        ?.takeIf { model.layout.title.showCategoryName && it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        general.textColor.toComposeColor().copy(alpha = 0.14f),
                        general.background.toComposeColor(),
                    ),
                ),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        model.gameIconPngBase64?.let { icon ->
            RunIcon(icon, gameTitle, Modifier.size(48.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            BasicText(
                text = gameTitle,
                style = TextStyle(
                    color = general.textColor.toComposeColor(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            categoryTitle?.let { category ->
                BasicText(
                    text = category,
                    style = TextStyle(
                        color = general.textColor.toComposeColor(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        if (model.layout.title.showAttemptCount && model.attemptCount > 0) {
            BasicText(
                text = model.attemptCount.toString(),
                style = TextStyle(
                    color = general.textColor.toComposeColor(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                ),
            )
        }
    }
}

@Composable
private fun SplitsViewport(
    model: RunBoardModel,
    modifier: Modifier = Modifier,
    onSegmentClick: (() -> Unit)? = null,
) {
    val layout = model.layout
    val slotHeight = 32.dp
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val capacity = (maxHeight / slotHeight).toInt().coerceAtLeast(1)
        val visibleCount = (layout.splits.visualSplitCount ?: capacity).coerceIn(1, capacity)
        val visibleSegments = selectVisibleSegments(model, visibleCount)
        val blankRowCount = if (layout.splits.fillWithBlankSpace) {
            (visibleCount - visibleSegments.size).coerceAtLeast(0)
        } else {
            0
        }

        Column(Modifier.fillMaxWidth()) {
            visibleSegments.forEachIndexed { index, segment ->
                SplitRow(segment, layout, onSegmentClick)
                if (layout.splits.showThinSeparators &&
                    (index != visibleSegments.lastIndex || blankRowCount > 0 || layout.splits.separatorLastSplit)
                ) {
                    SplitDivider(layout)
                }
            }

            repeat(blankRowCount) { index ->
                BlankSplitRow()
                if (layout.splits.showThinSeparators &&
                    (index != blankRowCount - 1 || layout.splits.separatorLastSplit)
                ) {
                    SplitDivider(layout)
                }
            }
        }
    }
}

@Composable
private fun SplitColumnHeader(layout: LayoutDefinition) {
    val muted = layout.general.notRunningColor.toComposeColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "Splits",
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        layout.splits.columns.asReversed().forEach { column ->
            BasicText(
                text = column.name,
                modifier = Modifier.width(72.dp),
                style = TextStyle(color = muted, fontSize = 11.sp, textAlign = TextAlign.End),
            )
        }
    }
}

@Composable
private fun SplitRow(
    segment: RunBoardSegmentModel,
    layout: LayoutDefinition,
    onClick: (() -> Unit)?,
) {
    val general = layout.general
    val rowModifier = Modifier
        .fillMaxWidth()
        .height(31.dp)
        .then(
            when {
                segment.isActive && layout.splits.currentSplitGradientStart !=
                    layout.splits.currentSplitGradientEnd -> Modifier.background(
                    brush = Brush.verticalGradient(
                        listOf(
                            layout.splits.currentSplitGradientStart.toComposeColor(),
                            layout.splits.currentSplitGradientEnd.toComposeColor(),
                        ),
                    ),
                )

                segment.isActive -> Modifier.background(
                    color = general.personalBestColor.toComposeColor().copy(alpha = 0.3f),
                )

                else -> Modifier
            },
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        )
        .padding(horizontal = 12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segment.iconPngBase64?.let { icon ->
            RunIcon(icon, segment.title, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        BasicText(
            text = segment.title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = general.textColor.toComposeColor(),
                fontSize = 15.sp,
                fontWeight = if (segment.isActive) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
        segment.columns.asReversed().forEach { column ->
            val text = column.valueMilliseconds?.let { value ->
                when (column.valueType) {
                    RunBoardColumnValueType.TIME -> formatDuration(value, layout.splits.splitTimeAccuracy)
                    RunBoardColumnValueType.DELTA -> formatDelta(
                        value,
                        layout.splits.deltaTimeAccuracy,
                        layout.splits.deltaDropDecimals,
                    )
                    RunBoardColumnValueType.SEGMENT_TIME -> formatDuration(
                        value,
                        layout.splits.segmentTimeAccuracy,
                    )
                }
            } ?: when (column.valueType) {
                RunBoardColumnValueType.TIME,
                RunBoardColumnValueType.SEGMENT_TIME,
                -> "—"
                RunBoardColumnValueType.DELTA -> if (
                    column.semanticColor == RunBoardSemanticColor.BEST_SEGMENT
                ) "—" else ""
            }
            BasicText(
                text = text,
                modifier = Modifier.width(72.dp),
                style = timerStyle(
                    color = column.semanticColor.toComposeColor(general),
                    size = 14,
                    textAlign = TextAlign.End,
                ),
            )
        }
    }
}

@Composable
private fun BlankSplitRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(31.dp),
    )
}

@Composable
private fun SplitDivider(layout: LayoutDefinition) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(layout.general.thinSeparatorsColor.toComposeColor()),
    )
}

@Composable
private fun RunTimer(model: RunBoardModel) {
    val layout = model.layout
    val general = layout.general
    val timerMilliseconds = runTimerMilliseconds(model)
    val timerColor = layout.timer.colorOverride?.toComposeColor()
        ?: model.timerSemanticColor.toComposeColor(general)
    val timerBrush = if (layout.timer.showGradient) {
        Brush.verticalGradient(
            listOf(
                lerp(timerColor, Color.White, 0.35f),
                lerp(timerColor, Color.Black, 0.2f),
            ),
        )
    } else {
        null
    }
    val timerBackground = if (layout.timer.showGradient) {
        Brush.verticalGradient(
            listOf(
                Color.Transparent,
                timerColor.copy(alpha = 0.18f),
            ),
        )
    } else {
        null
    }
    val timerHeight = layout.timer.height ?: 60
    val timerFontSize = (timerHeight * 0.8f).toInt().coerceAtLeast(16)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(timerHeight.dp)
            .clipToBounds()
            .then(timerBackground?.let { Modifier.background(it) } ?: Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        BasicText(
            text = formatTimerDuration(timerMilliseconds, layout.timer.accuracy),
            maxLines = 1,
            softWrap = false,
            style = timerStyle(timerColor, timerFontSize, TextAlign.End).let { style ->
                timerBrush?.let { style.copy(brush = it) } ?: style
            },
        )
    }
}

internal fun runTimerMilliseconds(model: RunBoardModel): Long {
    if (!model.layout.timer.isSegmentTimer) return model.elapsedMilliseconds

    return when (model.status) {
        RunStatus.FINISHED -> model.segments.lastOrNull()?.segmentTimeMilliseconds
        RunStatus.RUNNING,
        RunStatus.PAUSED,
        -> model.segments.firstOrNull { it.isActive }?.liveTimeMilliseconds
        RunStatus.READY -> 0L
    } ?: 0L
}

@Composable
private fun PreviousSegment(
    previous: RunBoardPreviousSegmentModel,
    layout: LayoutDefinition,
) {
    val general = layout.general
    val labelColor = layout.previousSegment.labelColor?.toComposeColor()
        ?: general.textColor.toComposeColor()
    val delta = previous.deltaMilliseconds
    val value = buildString {
        append(delta?.let {
            formatDelta(it, layout.previousSegment.accuracy, layout.previousSegment.dropDecimals)
        } ?: "—")
        if (layout.previousSegment.showPossibleTimeSave) {
            append(" / ")
            append(previous.possibleTimeSaveMilliseconds?.let {
                formatDuration(it, layout.previousSegment.accuracy)
            } ?: "—")
        }
    }
    val contentModifier = Modifier
        .fillMaxWidth()
        .background(general.textColor.toComposeColor().copy(alpha = 0.06f))
        .padding(horizontal = 12.dp, vertical = 7.dp)
    if (layout.previousSegment.displayTwoRows) {
        Column(modifier = contentModifier) {
            BasicText(
                text = previous.label,
                style = TextStyle(color = labelColor, fontSize = 14.sp),
            )
            BasicText(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = timerStyle(
                    color = previous.semanticColor.toComposeColor(general),
                    size = 14,
                    textAlign = TextAlign.End,
                ),
            )
        }
        return
    }
    Row(
        modifier = contentModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = previous.label,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = labelColor, fontSize = 14.sp),
        )
        BasicText(
            text = value,
            style = timerStyle(
                color = previous.semanticColor.toComposeColor(general),
                size = 14,
                textAlign = TextAlign.End,
            ),
        )
    }
}

private fun selectVisibleSegments(
    model: RunBoardModel,
    count: Int,
): List<RunBoardSegmentModel> {
    if (count >= model.segments.size) return model.segments

    if (model.status == RunStatus.FINISHED && model.layout.splits.alwaysShowLastSplit) {
        return model.segments.takeLast(count)
    }

    val activeIndex = model.segments.indexOfFirst { it.isActive }.takeIf { it >= 0 }
        ?: model.segments.indexOfLast { it.isCompleted }.takeIf { it >= 0 }
        ?: 0
    if (model.layout.splits.alwaysShowLastSplit && count > 1 && activeIndex < model.segments.lastIndex) {
        val scrollingCount = count - 1
        val previewCount = model.layout.splits.splitPreviewCount.coerceIn(0, scrollingCount - 1)
        var start = (activeIndex - previewCount).coerceAtLeast(0)
        var end = (start + scrollingCount).coerceAtMost(model.segments.lastIndex)
        start = (end - scrollingCount).coerceAtLeast(0)
        end = (start + scrollingCount).coerceAtMost(model.segments.lastIndex)
        return model.segments.subList(start, end) + model.segments.last()
    }

    val previewCount = model.layout.splits.splitPreviewCount.coerceIn(0, count - 1)
    var start = (activeIndex - previewCount).coerceAtLeast(0)
    var end = (start + count).coerceAtMost(model.segments.size)
    start = (end - count).coerceAtLeast(0)
    end = (start + count).coerceAtMost(model.segments.size)
    return model.segments.subList(start, end)
}

private fun RunBoardSemanticColor.toComposeColor(general: LayoutGeneral): Color = when (this) {
    RunBoardSemanticColor.DEFAULT -> general.textColor
    RunBoardSemanticColor.AHEAD_GAINING_TIME -> general.aheadGainingTimeColor
    RunBoardSemanticColor.AHEAD_LOSING_TIME -> general.aheadLosingTimeColor
    RunBoardSemanticColor.BEHIND_GAINING_TIME -> general.behindGainingTimeColor
    RunBoardSemanticColor.BEHIND_LOSING_TIME -> general.behindLosingTimeColor
    RunBoardSemanticColor.BEST_SEGMENT -> general.bestSegmentColor
    RunBoardSemanticColor.PERSONAL_BEST -> general.personalBestColor
    RunBoardSemanticColor.PAUSED -> general.pausedColor
    RunBoardSemanticColor.NOT_RUNNING -> general.notRunningColor
}.toComposeColor()

@Composable
@OptIn(ExperimentalEncodingApi::class)
private fun RunIcon(
    pngBase64: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(pngBase64) {
        runCatching { Base64.decode(pngBase64).decodeToImageBitmap() }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

private fun timerStyle(
    color: Color,
    size: Int,
    textAlign: TextAlign = TextAlign.Start,
) = TextStyle(
    color = color,
    fontFamily = FontFamily.Monospace,
    fontSize = size.sp,
    lineHeight = size.sp,
    fontWeight = FontWeight.Medium,
    textAlign = textAlign,
)

internal fun formatDuration(
    milliseconds: Long,
    accuracy: LayoutAccuracy = LayoutAccuracy.HUNDREDTHS,
): String {
    val safe = milliseconds.coerceAtLeast(0)
    val minutes = safe / 60_000
    val seconds = (safe / 1_000) % 60
    val fraction = safe % 1_000
    return when (accuracy) {
        LayoutAccuracy.SECONDS -> "$minutes:${seconds.toString().padStart(2, '0')}"
        LayoutAccuracy.TENTHS -> "$minutes:${seconds.toString().padStart(2, '0')}.${fraction / 100}"
        LayoutAccuracy.HUNDREDTHS -> "$minutes:${seconds.toString().padStart(2, '0')}.${(fraction / 10).toString().padStart(2, '0')}"
        LayoutAccuracy.MILLISECONDS -> "$minutes:${seconds.toString().padStart(2, '0')}.${fraction.toString().padStart(3, '0')}"
    }
}

internal fun formatTimerDuration(
    milliseconds: Long,
    accuracy: LayoutAccuracy = LayoutAccuracy.HUNDREDTHS,
): String {
    val formatted = formatDuration(milliseconds, accuracy)
    if (!formatted.startsWith("0:")) return formatted

    val seconds = formatted.substringAfter(':').trimStart('0')
    return when {
        seconds.isEmpty() -> "0"
        seconds.startsWith('.') -> "0$seconds"
        else -> seconds
    }
}

internal fun formatDelta(
    milliseconds: Long,
    accuracy: LayoutAccuracy,
    dropDecimals: Boolean,
): String {
    val displayAccuracy = if (dropDecimals && kotlin.math.abs(milliseconds) >= 60_000) {
        LayoutAccuracy.SECONDS
    } else {
        accuracy
    }
    val magnitude = formatTimerDuration(kotlin.math.abs(milliseconds), displayAccuracy)
    return if (milliseconds < 0) "−$magnitude" else "+$magnitude"
}

private fun LayoutColor.toComposeColor(): Color = Color(red, green, blue, alpha)
