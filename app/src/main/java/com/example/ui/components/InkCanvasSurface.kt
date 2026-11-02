package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.InkStroke
import com.example.data.InkTool
import com.example.data.PaperTemplate
import com.example.data.StrokePoint

/**
 * Resolves semantic ink palette colors so dark and light modes both remain crisp and legible.
 */
@Composable
fun resolveInkColor(colorKey: String, isDarkSurface: Boolean): Color {
    return when (colorKey) {
        "AMBER" -> MaterialTheme.colorScheme.primary
        "SLATE" -> MaterialTheme.colorScheme.onSurfaceVariant
        "BLUE" -> if (isDarkSurface) Color(0xFF60A5FA) else Color(0xFF2563EB)
        "GREEN" -> if (isDarkSurface) Color(0xFF4ADE80) else Color(0xFF16A34A)
        "CORAL" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface // "INK" adapts cleanly to light/dark page
    }
}

fun DrawScope.drawPaperTemplate(
    template: PaperTemplate,
    lineColor: Color,
    accentMarginColor: Color
) {
    val spacingPx = 28.dp.toPx()
    when (template) {
        PaperTemplate.BLANK -> Unit
        PaperTemplate.RULED -> {
            var y = spacingPx
            while (y < size.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += spacingPx
            }
        }
        PaperTemplate.GRID -> {
            val minorStrokePx = 1.dp.toPx()
            val majorStrokePx = 1.4.dp.toPx()
            var colIndex = 1
            var x = spacingPx
            while (x < size.width) {
                val isMajor = colIndex % 4 == 0
                drawLine(
                    color = if (isMajor) accentMarginColor else lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = if (isMajor) majorStrokePx else minorStrokePx
                )
                x += spacingPx
                colIndex++
            }
            var rowIndex = 1
            var y = spacingPx
            while (y < size.height) {
                val isMajor = rowIndex % 4 == 0
                drawLine(
                    color = if (isMajor) accentMarginColor else lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (isMajor) majorStrokePx else minorStrokePx
                )
                y += spacingPx
                rowIndex++
            }
        }
        PaperTemplate.DOTTED -> {
            val dotSpacing = 24.dp.toPx()
            val radius = 1.3.dp.toPx()
            var x = dotSpacing
            while (x < size.width) {
                var y = dotSpacing
                while (y < size.height) {
                    drawCircle(
                        color = accentMarginColor,
                        radius = radius,
                        center = Offset(x, y)
                    )
                    y += dotSpacing
                }
                x += dotSpacing
            }
        }
        PaperTemplate.CORNELL -> {
            var y = spacingPx * 1.5f
            val summaryTop = (size.height - 72.dp.toPx()).coerceAtLeast(spacingPx * 3f)
            while (y < summaryTop) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += spacingPx
            }
            val cueColumnX = (size.width * 0.28f).coerceAtMost(130.dp.toPx())
            drawLine(
                color = accentMarginColor,
                start = Offset(cueColumnX, 0f),
                end = Offset(cueColumnX, summaryTop),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = accentMarginColor,
                start = Offset(0f, summaryTop),
                end = Offset(size.width, summaryTop),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

fun DrawScope.drawSmoothStroke(
    points: List<StrokePoint>,
    color: Color,
    widthDp: Float,
    isHighlighter: Boolean
) {
    if (points.isEmpty()) return
    val paintColor = if (isHighlighter) color.copy(alpha = 0.34f) else color
    val avgPressure = points.map { it.pressure }.average().toFloat().coerceIn(0.65f, 1.35f)
    val effectiveWidthDp = if (isHighlighter) widthDp else widthDp * avgPressure
    val strokePx = effectiveWidthDp.dp.toPx()

    if (points.size == 1) {
        val pt = points.first()
        drawCircle(
            color = paintColor,
            radius = strokePx / 2f,
            center = Offset(pt.x.dp.toPx(), pt.y.dp.toPx())
        )
        return
    }

    val path = Path()
    val firstX = points[0].x.dp.toPx()
    val firstY = points[0].y.dp.toPx()
    path.moveTo(firstX, firstY)

    if (points.size == 2) {
        path.lineTo(points[1].x.dp.toPx(), points[1].y.dp.toPx())
    } else {
        for (i in 1 until points.size - 1) {
            val ctrlX = points[i].x.dp.toPx()
            val ctrlY = points[i].y.dp.toPx()
            val nextX = points[i + 1].x.dp.toPx()
            val nextY = points[i + 1].y.dp.toPx()
            val midX = (ctrlX + nextX) / 2f
            val midY = (ctrlY + nextY) / 2f
            path.quadraticTo(ctrlX, ctrlY, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x.dp.toPx(), last.y.dp.toPx())
    }

    drawPath(
        path = path,
        color = paintColor,
        style = Stroke(
            width = strokePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

@Composable
fun InkCanvasSurface(
    strokes: List<InkStroke>,
    template: PaperTemplate,
    interactive: Boolean,
    activeTool: InkTool,
    activeColorKey: String,
    activeStrokeWidth: Float,
    isDarkTheme: Boolean,
    canvasHeight: Dp = 360.dp,
    fillFullHeight: Boolean = false,
    onCommitStroke: (InkStroke) -> Unit,
    onEraseAt: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    FreehandDrawingSurface(
        strokes = strokes,
        modifier = modifier,
        template = template,
        interactive = interactive,
        activeTool = activeTool,
        activeColorKey = activeColorKey,
        activeStrokeWidth = activeStrokeWidth,
        isDarkTheme = isDarkTheme,
        canvasHeight = canvasHeight,
        fillFullHeight = fillFullHeight,
        onCommitStroke = onCommitStroke,
        onEraseSegmentSweep = { fromX, _, toX, toY, _ -> onEraseAt(toX.takeIf { !it.isNaN() } ?: fromX, toY) }
    )
}
