package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
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
import com.example.ui.theme.InkBlueDark
import com.example.ui.theme.InkBlueLight
import com.example.ui.theme.InkGreenDark
import com.example.ui.theme.InkGreenLight
import com.example.ui.theme.InkSlateDark
import com.example.ui.theme.InkSlateLight
import com.example.util.InkPaletteHelper
import com.example.util.StrokePressureEngine

/**
 * Resolves semantic Excalidraw whiteboard ink palette colors and user-defined "#RRGGBB" custom swatches.
 */
@Composable
fun resolveInkColor(colorKey: String, isDarkSurface: Boolean): Color {
    InkPaletteHelper.parseHexRgbToArgbInt(colorKey)?.let { argb ->
        return Color(argb)
    }
    return when (colorKey) {
        "AMBER" -> MaterialTheme.colorScheme.primary
        "SLATE" -> if (isDarkSurface) InkSlateDark else InkSlateLight
        "BLUE" -> if (isDarkSurface) InkBlueDark else InkBlueLight
        "GREEN" -> if (isDarkSurface) InkGreenDark else InkGreenLight
        "CORAL" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface // "INK" (#121212 light / #E5E5EC dark)
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
    isHighlighter: Boolean,
    isStylus: Boolean = false,
    extraWidthDp: Float = 0f
) {
    if (points.isEmpty()) return
    val paintColor = if (isHighlighter) color.copy(alpha = 0.34f) else color
    val useVariablePressure = StrokePressureEngine.hasVariablePressure(
        points = points,
        isStylusStroke = isStylus
    )

    if (useVariablePressure) {
        val spine = StrokePressureEngine.buildVariableWidthSpine(
            points = points,
            baseWidthDp = widthDp,
            isHighlighter = isHighlighter,
            extraWidthDp = extraWidthDp,
            maxStepDp = 1.8f
        )
        if (spine.isEmpty()) return
        if (spine.size == 1) {
            val s = spine[0]
            drawCircle(
                color = paintColor,
                radius = s.radiusDp.dp.toPx(),
                center = Offset(s.xDp.dp.toPx(), s.yDp.dp.toPx())
            )
            return
        }

        val ribbonPath = Path().apply {
            fillType = PathFillType.NonZero
            var prevX = spine[0].xDp.dp.toPx()
            var prevY = spine[0].yDp.dp.toPx()
            var prevR = spine[0].radiusDp.dp.toPx()
            addOval(
                oval = Rect(center = Offset(prevX, prevY), radius = prevR),
                direction = Path.Direction.Clockwise
            )

            for (i in 1 until spine.size) {
                val curX = spine[i].xDp.dp.toPx()
                val curY = spine[i].yDp.dp.toPx()
                val curR = spine[i].radiusDp.dp.toPx()

                addOval(
                    oval = Rect(center = Offset(curX, curY), radius = curR),
                    direction = Path.Direction.Clockwise
                )

                val quad = StrokePressureEngine.computeBitangentQuad(
                    x0 = prevX,
                    y0 = prevY,
                    r0 = prevR,
                    x1 = curX,
                    y1 = curY,
                    r1 = curR
                )
                if (quad != null) {
                    moveTo(quad.x0, quad.y0)
                    lineTo(quad.x1, quad.y1)
                    lineTo(quad.x2, quad.y2)
                    lineTo(quad.x3, quad.y3)
                    close()
                }

                prevX = curX
                prevY = curY
                prevR = curR
            }
        }

        drawPath(
            path = ribbonPath,
            color = paintColor
        )
        return
    }

    val strokePx = (widthDp + extraWidthDp).dp.toPx()
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
