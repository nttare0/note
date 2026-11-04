package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.InkStroke
import com.example.data.InkTool
import com.example.data.NoteImageAttachment
import com.example.data.PaperTemplate
import com.example.data.StrokePoint
import com.example.util.InkPaletteHelper
import com.example.util.LassoHitSelection
import com.example.util.LassoSelectionEngine
import com.example.util.StrokePressureEngine
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private enum class LassoGestureSubMode {
    DRAW_LASSO,
    MOVE_SELECTION,
    SCALE_SELECTION,
    ROTATE_SELECTION
}

private class StylusMotionSnapshot {
    var isNativeStylus: Boolean = false
    var isNativeEraser: Boolean = false
    var axisMinPressure: Float = 0f
    var axisMaxPressure: Float = 1f
    var latestPointerId: Int = -1
    var currentPressure: Float = -1f
    var historicalCount: Int = 0
    var historicalPressures: FloatArray = FloatArray(32)

    fun record(event: MotionEvent) {
        val ptrIndex = if (latestPointerId >= 0) {
            event.findPointerIndex(latestPointerId).takeIf { it >= 0 }
        } else {
            null
        } ?: event.actionIndex.coerceIn(0, (event.pointerCount - 1).coerceAtLeast(0))

        val tool = event.getToolType(ptrIndex)
        isNativeStylus = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
        isNativeEraser = tool == MotionEvent.TOOL_TYPE_ERASER

        val range = event.device?.getMotionRange(MotionEvent.AXIS_PRESSURE)
        axisMinPressure = range?.min ?: 0f
        axisMaxPressure = range?.max ?: 1f
        currentPressure = event.getPressure(ptrIndex)

        val hSize = event.historySize
        if (hSize > historicalPressures.size) {
            historicalPressures = FloatArray(hSize + 16)
        }
        for (h in 0 until hSize) {
            historicalPressures[h] = event.getHistoricalPressure(ptrIndex, h)
        }
        historicalCount = hSize
    }
}

/**
 * Dedicated Jetpack Compose freehand handwriting, segment-eraser, canvas-image, and Lasso
 * selection/transformation surface component.
 *
 * - Captures zero-slop raw touch & stylus pointer events (`awaitEachGesture`).
 * - Harvests intermediate historical motion samples (`change.historical`) for smooth handwriting
 *   and gap-free swept segment erasing.
 * - Supports a Lasso Selection mode (`InkTool.LASSO`) allowing users to draw a dashed boundary
 *   around strokes and canvas-placed images, then move (drag), scale (corner handles or pinch),
 *   or rotate (top handle or two-finger twist) the selected elements.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FreehandDrawingSurface(
    strokes: List<InkStroke>,
    modifier: Modifier = Modifier,
    canvasImages: List<NoteImageAttachment> = emptyList(),
    lassoSelection: LassoHitSelection = LassoHitSelection(),
    template: PaperTemplate = PaperTemplate.BLANK,
    interactive: Boolean = true,
    activeTool: InkTool = InkTool.PEN,
    activeColorKey: String = "INK",
    activeStrokeWidth: Float = 3.4f,
    isPressureSensitive: Boolean = true,
    isDarkTheme: Boolean = false,
    canvasHeight: Dp = 360.dp,
    fillFullHeight: Boolean = false,
    showEmptyHint: Boolean = true,
    onCommitStroke: (InkStroke) -> Unit,
    onEraseSegmentSweep: (
        fromXDp: Float,
        fromYDp: Float,
        toXDp: Float,
        toYDp: Float,
        eraserRadiusDp: Float
    ) -> Unit = { _, _, _, _, _ -> },
    onEraseGestureStart: () -> Unit = {},
    onEraseGestureEnd: () -> Unit = {},
    onCompleteLassoSelection: (List<StrokePoint>) -> Unit = {},
    onLassoTransformStart: () -> Unit = {},
    onLassoTransformDelta: (
        pivotXDp: Float,
        pivotYDp: Float,
        dxDp: Float,
        dyDp: Float,
        scaleFactor: Float,
        rotationDeltaDeg: Float
    ) -> Unit = { _, _, _, _, _, _ -> },
    onLassoTransformEnd: () -> Unit = {},
    onLongPressSelectElement: (Float, Float) -> Boolean = { _, _ -> false },
    onDeleteSelectedElements: (() -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val motionSnapshot = remember { StylusMotionSnapshot() }
    val activeStrokePoints = remember { mutableStateListOf<StrokePoint>() }
    val activeLassoPoints = remember { mutableStateListOf<StrokePoint>() }
    val activeEraserTrail = remember { mutableStateListOf<Offset>() }
    var activeCursorOffset by remember { mutableStateOf<Offset?>(null) }
    var isCurrentlyErasing by remember { mutableStateOf(false) }
    var isActiveStylusStroke by remember { mutableStateOf(false) }
    var activeStylusPressure by remember { mutableFloatStateOf(1.0f) }

    val placedCanvasImages = remember(canvasImages) {
        canvasImages.filter { it.placedOnCanvas }
    }
    val selectionBounds = remember(strokes, placedCanvasImages, lassoSelection) {
        LassoSelectionEngine.computeSelectionBounds(
            strokes = strokes,
            images = placedCanvasImages,
            selection = lassoSelection
        )
    }

    val latestStrokes by rememberUpdatedState(strokes)
    val latestPlacedImages by rememberUpdatedState(placedCanvasImages)
    val latestTool by rememberUpdatedState(activeTool)
    val latestColorKey by rememberUpdatedState(activeColorKey)
    val latestWidth by rememberUpdatedState(activeStrokeWidth)
    val latestPressureEnabled by rememberUpdatedState(isPressureSensitive)
    val latestSelectionBounds by rememberUpdatedState(selectionBounds)
    val latestCommit by rememberUpdatedState(onCommitStroke)
    val latestEraseSweep by rememberUpdatedState(onEraseSegmentSweep)
    val latestEraseStart by rememberUpdatedState(onEraseGestureStart)
    val latestEraseEnd by rememberUpdatedState(onEraseGestureEnd)
    val latestCompleteLasso by rememberUpdatedState(onCompleteLassoSelection)
    val latestLassoTransformStart by rememberUpdatedState(onLassoTransformStart)
    val latestLassoTransformDelta by rememberUpdatedState(onLassoTransformDelta)
    val latestLassoTransformEnd by rememberUpdatedState(onLassoTransformEnd)
    val latestLongPressSelect by rememberUpdatedState(onLongPressSelectElement)

    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isDarkTheme) 0.55f else 0.7f)
    val marginColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val eraserRingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val eraserFillColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    val eraserTrailColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val lassoAccentColor = MaterialTheme.colorScheme.primary
    val handleSurfaceColor = MaterialTheme.colorScheme.surface
    val semanticsLabel = stringResource(R.string.canvas_semantics_desc)

    // Pre-resolve semantic ink colors for DrawScope + custom #RRGGBB hex swatches
    val inkDefault = resolveInkColor("INK", isDarkTheme)
    val inkAmber = resolveInkColor("AMBER", isDarkTheme)
    val inkSlate = resolveInkColor("SLATE", isDarkTheme)
    val inkBlue = resolveInkColor("BLUE", isDarkTheme)
    val inkGreen = resolveInkColor("GREEN", isDarkTheme)
    val inkCoral = resolveInkColor("CORAL", isDarkTheme)

    fun colorFor(key: String): Color {
        InkPaletteHelper.parseHexRgbToArgbInt(key)?.let { return Color(it) }
        return when (key) {
            "AMBER" -> inkAmber
            "SLATE" -> inkSlate
            "BLUE" -> inkBlue
            "GREEN" -> inkGreen
            "CORAL" -> inkCoral
            else -> inkDefault
        }
    }

    val sizeModifier = if (fillFullHeight) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxWidth()
            .height(canvasHeight)
    }

    val touchCaptureModifier = if (interactive) {
        Modifier
            .motionEventSpy { motionSnapshot.record(it) }
            .pointerInput(density) {
            val minSampleDistDp = 0.55f

            fun shortestAngleDeltaDeg(fromRad: Float, toRad: Float): Float {
                var diffDeg = ((toRad - fromRad) * (180.0 / PI)).toFloat()
                while (diffDeg > 180f) diffDeg -= 360f
                while (diffDeg < -180f) diffDeg += 360f
                return diffDeg
            }

            awaitEachGesture {
                motionSnapshot.latestPointerId = -1
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()

                val activePointerId = down.id
                motionSnapshot.latestPointerId = activePointerId.value.toInt()
                val isStylus = down.type == PointerType.Stylus ||
                    down.type == PointerType.Eraser ||
                    motionSnapshot.isNativeStylus

                val axisMinPressure = motionSnapshot.axisMinPressure
                val axisMaxPressure = motionSnapshot.axisMaxPressure

                val effectiveTool = if (down.type == PointerType.Eraser || motionSnapshot.isNativeEraser) {
                    InkTool.ERASER
                } else {
                    latestTool
                }
                val applyStylusPressure = isStylus && latestPressureEnabled &&
                    (effectiveTool == InkTool.PEN || effectiveTool == InkTool.MARKER)

                val eraserRadiusDp = if (effectiveTool == InkTool.ERASER) {
                    if (latestTool == InkTool.ERASER) latestWidth.coerceIn(8f, 40f) else 18f
                } else {
                    18f
                }

                fun Offset.toClampedStrokePoint(pressure: Float): StrokePoint {
                    val clampedX = x.coerceIn(0f, size.width.toFloat())
                    val clampedY = y.coerceIn(0f, size.height.toFloat())
                    val xDp = with(density) { clampedX.toDp().value }
                    val yDp = with(density) { clampedY.toDp().value }
                    return StrokePoint(x = xDp, y = yDp, pressure = pressure)
                }

                fun Offset.toRawDpPair(): Pair<Float, Float> {
                    return with(density) { x.toDp().value to y.toDp().value }
                }

                // Branch 1: LASSO SELECTION & TRANSFORM MODE
                if (effectiveTool == InkTool.LASSO) {
                    isCurrentlyErasing = false
                    isActiveStylusStroke = false
                    activeStrokePoints.clear()
                    activeEraserTrail.clear()
                    activeCursorOffset = null

                    val (rawStartXDp, rawStartYDp) = down.position.toRawDpPair()
                    val boundsAtStart = latestSelectionBounds

                    val subMode: LassoGestureSubMode = if (boundsAtStart != null) {
                        val rotKnobX = boundsAtStart.centerX
                        val rotKnobY = max(14f, boundsAtStart.top - 26f)
                        val corners = listOf(
                            boundsAtStart.left to boundsAtStart.top,
                            boundsAtStart.right to boundsAtStart.top,
                            boundsAtStart.right to boundsAtStart.bottom,
                            boundsAtStart.left to boundsAtStart.bottom
                        )
                        when {
                            hypot(rawStartXDp - rotKnobX, rawStartYDp - rotKnobY) <= 22f ->
                                LassoGestureSubMode.ROTATE_SELECTION
                            corners.any { (cx, cy) -> hypot(rawStartXDp - cx, rawStartYDp - cy) <= 20f } ->
                                LassoGestureSubMode.SCALE_SELECTION
                            boundsAtStart.contains(rawStartXDp, rawStartYDp, paddingDp = 8f) ->
                                LassoGestureSubMode.MOVE_SELECTION
                            else -> LassoGestureSubMode.DRAW_LASSO
                        }
                    } else {
                        LassoGestureSubMode.DRAW_LASSO
                    }

                    if (subMode == LassoGestureSubMode.DRAW_LASSO) {
                        activeLassoPoints.clear()
                        activeLassoPoints.add(down.position.toClampedStrokePoint(1.0f))

                        var pointerActive = true
                        while (pointerActive) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == activePointerId }
                            if (change == null || !change.pressed) {
                                pointerActive = false
                            } else {
                                change.consume()
                                change.historical.forEach { hist ->
                                    val pt = hist.position.toClampedStrokePoint(1.0f)
                                    val last = activeLassoPoints.lastOrNull()
                                    if (last == null || hypot(pt.x - last.x, pt.y - last.y) >= 1.2f) {
                                        activeLassoPoints.add(pt)
                                    }
                                }
                                val curPt = change.position.toClampedStrokePoint(1.0f)
                                val last = activeLassoPoints.lastOrNull()
                                if (last == null || hypot(curPt.x - last.x, curPt.y - last.y) >= 1.2f) {
                                    activeLassoPoints.add(curPt)
                                }
                            }
                        }

                        val completedLoop = activeLassoPoints.toList()
                        activeLassoPoints.clear()
                        latestCompleteLasso(completedLoop)
                    } else if (boundsAtStart != null) {
                        latestLassoTransformStart()
                        var pivotX = boundsAtStart.centerX
                        var pivotY = boundsAtStart.centerY
                        var prevMoveX = rawStartXDp
                        var prevMoveY = rawStartYDp
                        var prevHandleDist = hypot(rawStartXDp - pivotX, rawStartYDp - pivotY).coerceAtLeast(8f)
                        var prevHandleAngleRad = atan2(rawStartYDp - pivotY, rawStartXDp - pivotX)

                        var wasMultiTouch = false
                        var prevMultiCenterX = 0f
                        var prevMultiCenterY = 0f
                        var prevMultiSpan = 0f
                        var prevMultiAngleRad = 0f

                        var anyPressed = true
                        while (anyPressed) {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }
                            if (pressedChanges.isEmpty()) {
                                anyPressed = false
                            } else {
                                pressedChanges.forEach { it.consume() }

                                if (pressedChanges.size >= 2) {
                                    val (p0x, p0y) = pressedChanges[0].position.toRawDpPair()
                                    val (p1x, p1y) = pressedChanges[1].position.toRawDpPair()
                                    val cx = (p0x + p1x) / 2f
                                    val cy = (p0y + p1y) / 2f
                                    val span = hypot(p1x - p0x, p1y - p0y).coerceAtLeast(8f)
                                    val angleRad = atan2(p1y - p0y, p1x - p0x)

                                    if (wasMultiTouch) {
                                        val dx = cx - prevMultiCenterX
                                        val dy = cy - prevMultiCenterY
                                        val ratio = (span / prevMultiSpan.coerceAtLeast(8f)).coerceIn(0.84f, 1.18f)
                                        val rotDeg = shortestAngleDeltaDeg(prevMultiAngleRad, angleRad)
                                        latestLassoTransformDelta(pivotX, pivotY, dx, dy, ratio, rotDeg)
                                        pivotX += dx
                                        pivotY += dy
                                    }
                                    prevMultiCenterX = cx
                                    prevMultiCenterY = cy
                                    prevMultiSpan = span
                                    prevMultiAngleRad = angleRad
                                    wasMultiTouch = true
                                } else {
                                    val primary = pressedChanges.first()
                                    val (curX, curY) = primary.position.toRawDpPair()
                                    if (wasMultiTouch) {
                                        wasMultiTouch = false
                                        prevMoveX = curX
                                        prevMoveY = curY
                                        prevHandleDist = hypot(curX - pivotX, curY - pivotY).coerceAtLeast(8f)
                                        prevHandleAngleRad = atan2(curY - pivotY, curX - pivotX)
                                    } else {
                                        when (subMode) {
                                            LassoGestureSubMode.MOVE_SELECTION -> {
                                                val dx = curX - prevMoveX
                                                val dy = curY - prevMoveY
                                                if (hypot(dx, dy) >= 0.1f) {
                                                    latestLassoTransformDelta(pivotX, pivotY, dx, dy, 1f, 0f)
                                                    pivotX += dx
                                                    pivotY += dy
                                                    prevMoveX = curX
                                                    prevMoveY = curY
                                                }
                                            }
                                            LassoGestureSubMode.SCALE_SELECTION -> {
                                                val curDist = hypot(curX - pivotX, curY - pivotY).coerceAtLeast(8f)
                                                val ratio = (curDist / prevHandleDist).coerceIn(0.82f, 1.22f)
                                                if (kotlin.math.abs(ratio - 1f) >= 0.004f) {
                                                    latestLassoTransformDelta(pivotX, pivotY, 0f, 0f, ratio, 0f)
                                                    prevHandleDist = curDist
                                                }
                                            }
                                            LassoGestureSubMode.ROTATE_SELECTION -> {
                                                val curAngleRad = atan2(curY - pivotY, curX - pivotX)
                                                val deltaDeg = shortestAngleDeltaDeg(prevHandleAngleRad, curAngleRad)
                                                if (kotlin.math.abs(deltaDeg) >= 0.25f) {
                                                    latestLassoTransformDelta(pivotX, pivotY, 0f, 0f, 1f, deltaDeg)
                                                    prevHandleAngleRad = curAngleRad
                                                }
                                            }
                                            LassoGestureSubMode.DRAW_LASSO -> Unit
                                        }
                                    }
                                }
                            }
                        }
                        latestLassoTransformEnd()
                    }
                    return@awaitEachGesture
                }

                // Branch 2: PEN, MARKER & SEGMENT ERASER MODES
                activeLassoPoints.clear()
                activeCursorOffset = down.position
                var smoothedPressure = if (applyStylusPressure) {
                    StrokePressureEngine.normalizeStylusPressure(
                        rawPressure = down.pressure,
                        minPressure = axisMinPressure,
                        maxPressure = axisMaxPressure
                    )
                } else {
                    StrokePressureEngine.NEUTRAL_NON_STYLUS_PRESSURE
                }
                var prevRawStylusPressure = smoothedPressure
                isActiveStylusStroke = applyStylusPressure
                activeStylusPressure = smoothedPressure

                val startPoint = down.position.toClampedStrokePoint(smoothedPressure)
                var prevErasePt = startPoint

                if (effectiveTool == InkTool.ERASER) {
                    isCurrentlyErasing = true
                    isActiveStylusStroke = false
                    activeStrokePoints.clear()
                    activeEraserTrail.clear()
                    activeEraserTrail.add(down.position)
                    latestEraseStart()
                    latestEraseSweep(
                        startPoint.x,
                        startPoint.y,
                        startPoint.x,
                        startPoint.y,
                        eraserRadiusDp
                    )
                } else {
                    isCurrentlyErasing = false
                    activeEraserTrail.clear()
                    activeStrokePoints.clear()
                    activeStrokePoints.add(startPoint)
                }

                var pointerActive = true

                // Stationary Long-Press to Select & Delete existing stroke or canvas image
                if (effectiveTool != InkTool.ERASER) {
                    val hasUnderlyingElement = !LassoSelectionEngine.findElementAtPoint(
                        xDp = startPoint.x,
                        yDp = startPoint.y,
                        strokes = latestStrokes,
                        images = latestPlacedImages
                    ).isEmpty

                    if (hasUnderlyingElement) {
                        var movedBeyondHoldSlop = false
                        val completedBeforeTimeout = withTimeoutOrNull(420L) {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == activePointerId }
                                    ?: ev.changes.firstOrNull { !isStylus || it.type == PointerType.Stylus || it.type == PointerType.Eraser }
                                if (ch == null || !ch.pressed) {
                                    pointerActive = false
                                    break
                                }
                                ch.consume()
                                activeCursorOffset = ch.position
                                val ptNow = ch.position.toClampedStrokePoint(smoothedPressure)
                                val dispDp = hypot(ptNow.x - startPoint.x, ptNow.y - startPoint.y)
                                if (dispDp >= 6.5f) {
                                    movedBeyondHoldSlop = true
                                    activeStrokePoints.add(ptNow)
                                    break
                                }
                            }
                            true
                        }

                        if (completedBeforeTimeout == null && !movedBeyondHoldSlop && pointerActive) {
                            // Stationary long-press triggered on an existing stroke or canvas image
                            activeStrokePoints.clear()
                            activeCursorOffset = null
                            isActiveStylusStroke = false
                            val selected = latestLongPressSelect(startPoint.x, startPoint.y)
                            if (selected) {
                                while (pointerActive) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == activePointerId }
                                    if (ch == null || !ch.pressed) {
                                        pointerActive = false
                                    } else {
                                        ch.consume()
                                    }
                                }
                                return@awaitEachGesture
                            }
                        }
                    }
                }

                while (pointerActive) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == activePointerId }
                        ?: event.changes.firstOrNull { !isStylus || it.type == PointerType.Stylus || it.type == PointerType.Eraser }

                    if (change == null || !change.pressed) {
                        pointerActive = false
                    } else {
                        change.consume()
                        activeCursorOffset = change.position

                        if (effectiveTool == InkTool.ERASER) {
                            change.historical.forEach { hist ->
                                val pt = hist.position.toClampedStrokePoint(1.0f)
                                latestEraseSweep(
                                    prevErasePt.x,
                                    prevErasePt.y,
                                    pt.x,
                                    pt.y,
                                    eraserRadiusDp
                                )
                                prevErasePt = pt
                                activeEraserTrail.add(hist.position)
                            }
                            val currentPt = change.position.toClampedStrokePoint(1.0f)
                            latestEraseSweep(
                                prevErasePt.x,
                                prevErasePt.y,
                                currentPt.x,
                                currentPt.y,
                                eraserRadiusDp
                            )
                            prevErasePt = currentPt
                            activeEraserTrail.add(change.position)
                            while (activeEraserTrail.size > 12) {
                                activeEraserTrail.removeAt(0)
                            }
                        } else {
                            val currentNormalizedRaw = if (applyStylusPressure) {
                                val rawNow = if (motionSnapshot.currentPressure > 0f) {
                                    motionSnapshot.currentPressure
                                } else {
                                    change.pressure
                                }
                                StrokePressureEngine.normalizeStylusPressure(
                                    rawPressure = rawNow,
                                    minPressure = axisMinPressure,
                                    maxPressure = axisMaxPressure
                                )
                            } else {
                                StrokePressureEngine.NEUTRAL_NON_STYLUS_PRESSURE
                            }

                            val histList = change.historical
                            val histCount = histList.size
                            for (hIdx in 0 until histCount) {
                                val hist = histList[hIdx]
                                val samplePressure = if (applyStylusPressure) {
                                    val histNormalized = if (hIdx < motionSnapshot.historicalCount) {
                                        StrokePressureEngine.normalizeStylusPressure(
                                            rawPressure = motionSnapshot.historicalPressures[hIdx],
                                            minPressure = axisMinPressure,
                                            maxPressure = axisMaxPressure
                                        )
                                    } else {
                                        val frac = (hIdx + 1f) / (histCount + 1f)
                                        prevRawStylusPressure + (currentNormalizedRaw - prevRawStylusPressure) * frac
                                    }
                                    smoothedPressure = StrokePressureEngine.smoothNextPressure(
                                        previousSmoothed = smoothedPressure,
                                        targetNormalized = histNormalized
                                    )
                                    smoothedPressure
                                } else {
                                    StrokePressureEngine.NEUTRAL_NON_STYLUS_PRESSURE
                                }

                                val candidate = hist.position.toClampedStrokePoint(samplePressure)
                                val last = activeStrokePoints.lastOrNull()
                                val dist = if (last != null) hypot(candidate.x - last.x, candidate.y - last.y) else Float.MAX_VALUE
                                val pressureShift = if (last != null) abs(candidate.pressure - last.pressure) else 0f
                                if (last == null || dist >= minSampleDistDp || (dist >= 0.12f && pressureShift >= 0.05f)) {
                                    activeStrokePoints.add(candidate)
                                }
                            }

                            val finalPressure = if (applyStylusPressure) {
                                smoothedPressure = StrokePressureEngine.smoothNextPressure(
                                    previousSmoothed = smoothedPressure,
                                    targetNormalized = currentNormalizedRaw
                                )
                                prevRawStylusPressure = currentNormalizedRaw
                                activeStylusPressure = smoothedPressure
                                smoothedPressure
                            } else {
                                StrokePressureEngine.NEUTRAL_NON_STYLUS_PRESSURE
                            }

                            val currentPt = change.position.toClampedStrokePoint(finalPressure)
                            val last = activeStrokePoints.lastOrNull()
                            val dist = if (last != null) hypot(currentPt.x - last.x, currentPt.y - last.y) else Float.MAX_VALUE
                            val pressureShift = if (last != null) abs(currentPt.pressure - last.pressure) else 0f
                            if (last == null || dist >= minSampleDistDp || (dist >= 0.12f && pressureShift >= 0.05f)) {
                                activeStrokePoints.add(currentPt)
                            }
                        }
                    }
                }

                activeCursorOffset = null
                activeEraserTrail.clear()
                isActiveStylusStroke = false
                if (effectiveTool == InkTool.ERASER) {
                    isCurrentlyErasing = false
                    latestEraseEnd()
                } else if (activeStrokePoints.isNotEmpty()) {
                    latestCommit(
                        InkStroke(
                            id = UUID.randomUUID().toString(),
                            points = activeStrokePoints.toList(),
                            colorKey = latestColorKey,
                            widthDp = latestWidth,
                            isHighlighter = effectiveTool == InkTool.MARKER,
                            isStylus = applyStylusPressure
                        )
                    )
                    activeStrokePoints.clear()
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .shadow(
                elevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                spotColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
            )
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium
            )
            .semantics { contentDescription = semanticsLabel }
            .testTag("freehand_drawing_surface")
    ) {
        // Layer 1: Paper background template (blank, ruled, grid, dotted, Cornell)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            drawPaperTemplate(
                template = template,
                lineColor = lineColor,
                accentMarginColor = marginColor
            )
        }

        // Layer 2: Canvas-placed image elements (moveable, scalable, and rotatable via Lasso)
        if (placedCanvasImages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .testTag("canvas_placed_images_layer")
            ) {
                placedCanvasImages.forEach { img ->
                    key(img.id) {
                        val isImageSelected = activeTool == InkTool.LASSO &&
                            img.id in lassoSelection.selectedImageIds
                        AsyncImage(
                            model = File(img.localPath),
                            contentDescription = stringResource(R.string.note_attached_image_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .offset(x = img.canvasXDp.dp, y = img.canvasYDp.dp)
                                .size(width = img.canvasWidthDp.dp, height = img.canvasHeightDp.dp)
                                .graphicsLayer {
                                    rotationZ = img.rotationDeg
                                }
                                .clip(MaterialTheme.shapes.extraSmall)
                                .border(
                                    width = if (isImageSelected) 1.5.dp else 0.75.dp,
                                    color = if (isImageSelected) {
                                        lassoAccentColor
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                                    },
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                        )
                    }
                }
            }
        }

        // Layer 3: Interactive freehand ink strokes, eraser reticle, and Lasso selection overlay
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .then(touchCaptureModifier)
                .testTag("ink_canvas_surface")
        ) {
            // 1. Subtle selection halo behind selected strokes when in Lasso mode
            if (activeTool == InkTool.LASSO && lassoSelection.selectedStrokeIds.isNotEmpty()) {
                strokes.forEach { stroke ->
                    if (stroke.id in lassoSelection.selectedStrokeIds) {
                        drawSmoothStroke(
                            points = stroke.points,
                            color = lassoAccentColor.copy(alpha = 0.24f),
                            widthDp = stroke.widthDp,
                            isHighlighter = false,
                            isStylus = stroke.isStylus,
                            extraWidthDp = 5f
                        )
                    }
                }
            }

            // 2. Committed handwriting strokes
            strokes.forEach { stroke ->
                drawSmoothStroke(
                    points = stroke.points,
                    color = colorFor(stroke.colorKey),
                    widthDp = stroke.widthDp,
                    isHighlighter = stroke.isHighlighter,
                    isStylus = stroke.isStylus
                )
            }

            // 3. In-flight real-time handwriting stroke preview
            if (activeStrokePoints.isNotEmpty() && !isCurrentlyErasing &&
                latestTool != InkTool.ERASER && latestTool != InkTool.LASSO
            ) {
                drawSmoothStroke(
                    points = activeStrokePoints,
                    color = colorFor(latestColorKey),
                    widthDp = latestWidth,
                    isHighlighter = latestTool == InkTool.MARKER,
                    isStylus = isActiveStylusStroke
                )
            }

            // 4. Live freehand dashed Lasso loop while drawing a boundary
            if (activeLassoPoints.size >= 2) {
                val loopPath = Path().apply {
                    moveTo(activeLassoPoints.first().x.dp.toPx(), activeLassoPoints.first().y.dp.toPx())
                    for (i in 1 until activeLassoPoints.size) {
                        lineTo(activeLassoPoints[i].x.dp.toPx(), activeLassoPoints[i].y.dp.toPx())
                    }
                    close()
                }
                drawPath(
                    path = loopPath,
                    color = lassoAccentColor.copy(alpha = 0.08f)
                )
                drawPath(
                    path = loopPath,
                    color = lassoAccentColor,
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(7.dp.toPx(), 5.dp.toPx()),
                            0f
                        )
                    )
                )
            }

            // 5. Active Lasso Selection Bounding Box, Corner Scale Handles, and Top Rotation Handle
            val activeBounds = selectionBounds
            if (interactive && activeTool == InkTool.LASSO && activeBounds != null) {
                val leftPx = activeBounds.left.dp.toPx()
                val topPx = activeBounds.top.dp.toPx()
                val widthPx = activeBounds.width.dp.toPx()
                val heightPx = activeBounds.height.dp.toPx()
                val centerXPx = activeBounds.centerX.dp.toPx()
                val rotKnobYPx = max(14f, activeBounds.top - 26f).dp.toPx()

                // Subtle selection interior tint
                drawRect(
                    color = lassoAccentColor.copy(alpha = 0.05f),
                    topLeft = Offset(leftPx, topPx),
                    size = Size(widthPx, heightPx)
                )

                // Dashed selection frame
                drawRect(
                    color = lassoAccentColor,
                    topLeft = Offset(leftPx, topPx),
                    size = Size(widthPx, heightPx),
                    style = Stroke(
                        width = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                            0f
                        )
                    )
                )

                // Stem connecting top-center of frame to Rotation Knob
                drawLine(
                    color = lassoAccentColor,
                    start = Offset(centerXPx, topPx),
                    end = Offset(centerXPx, rotKnobYPx),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Top Rotation Handle Knob
                val rotRadiusPx = 6.5.dp.toPx()
                drawCircle(
                    color = handleSurfaceColor,
                    radius = rotRadiusPx,
                    center = Offset(centerXPx, rotKnobYPx)
                )
                drawCircle(
                    color = lassoAccentColor,
                    radius = rotRadiusPx,
                    center = Offset(centerXPx, rotKnobYPx),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Four Excalidraw-style Rounded-Square Corner Resize Handles
                val handleSidePx = 10.dp.toPx()
                val halfSidePx = handleSidePx / 2f
                val handleCornerPx = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                val cornersPx = listOf(
                    Offset(leftPx, topPx),
                    Offset(leftPx + widthPx, topPx),
                    Offset(leftPx + widthPx, topPx + heightPx),
                    Offset(leftPx, topPx + heightPx)
                )
                cornersPx.forEach { corner ->
                    val topLeft = Offset(corner.x - halfSidePx, corner.y - halfSidePx)
                    drawRoundRect(
                        color = handleSurfaceColor,
                        topLeft = topLeft,
                        size = Size(handleSidePx, handleSidePx),
                        cornerRadius = handleCornerPx
                    )
                    drawRoundRect(
                        color = lassoAccentColor,
                        topLeft = topLeft,
                        size = Size(handleSidePx, handleSidePx),
                        cornerRadius = handleCornerPx,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // 6. Swept eraser trail & live touch/stylus cursor feedback
            val cursor = activeCursorOffset
            if (cursor != null && interactive && latestTool != InkTool.LASSO) {
                if (isCurrentlyErasing || latestTool == InkTool.ERASER) {
                    val eraserRadiusPx = (if (latestTool == InkTool.ERASER) latestWidth else 18f)
                        .coerceIn(8f, 40f).dp.toPx()

                    if (activeEraserTrail.size >= 2) {
                        val trailPath = Path().apply {
                            moveTo(activeEraserTrail.first().x, activeEraserTrail.first().y)
                            for (i in 1 until activeEraserTrail.size) {
                                lineTo(activeEraserTrail[i].x, activeEraserTrail[i].y)
                            }
                        }
                        drawPath(
                            path = trailPath,
                            color = eraserTrailColor,
                            style = Stroke(
                                width = eraserRadiusPx * 2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    drawCircle(
                        color = eraserFillColor,
                        radius = eraserRadiusPx,
                        center = cursor
                    )
                    drawCircle(
                        color = eraserRingColor,
                        radius = eraserRadiusPx,
                        center = cursor,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                } else {
                    val liveNibWidthDp = if (isActiveStylusStroke) {
                        StrokePressureEngine.computePointWidthDp(
                            baseWidthDp = latestWidth,
                            pressure = activeStylusPressure,
                            isHighlighter = latestTool == InkTool.MARKER,
                            pressureEnabled = true
                        )
                    } else {
                        latestWidth
                    }
                    drawCircle(
                        color = colorFor(latestColorKey).copy(alpha = 0.18f),
                        radius = (liveNibWidthDp.dp.toPx() * 1.35f).coerceAtLeast(5.dp.toPx()),
                        center = cursor
                    )
                }
            }
        }

        // Subtle live stylus pressure readout pill when actively writing with a pressure-sensitive stylus
        AnimatedVisibility(
            visible = interactive && isActiveStylusStroke,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .testTag("stylus_live_pressure_badge")
        ) {
            val pct = (activeStylusPressure.coerceIn(0f, 1.35f) * 100f).roundToInt().coerceIn(5, 135)
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = stringResource(R.string.stylus_pressure_live_format, pct),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Floating Contextual Delete / Deselect Pill when canvas elements are selected (via Long-Press or Lasso)
        if (interactive && activeTool == InkTool.LASSO && selectionBounds != null && onDeleteSelectedElements != null) {
            val anchorX = (selectionBounds.left - 4f).coerceAtLeast(8f)
            val anchorY = if (selectionBounds.top >= 68f) {
                (selectionBounds.top - 58f).coerceAtLeast(8f)
            } else {
                (selectionBounds.bottom + 12f)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .offset(x = anchorX.dp, y = anchorY.dp)
                    .height(48.dp)
                    .shadow(4.dp, MaterialTheme.shapes.small)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .padding(horizontal = 4.dp)
                    .testTag("canvas_selection_quick_bar")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = onDeleteSelectedElements)
                        .padding(horizontal = 10.dp)
                        .testTag("canvas_quick_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.lasso_delete_selection_desc),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.canvas_quick_delete_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (onClearSelection != null) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClick = onClearSelection)
                            .testTag("canvas_quick_deselect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.lasso_clear_selection_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showEmptyHint && interactive &&
                strokes.isEmpty() && placedCanvasImages.isEmpty() && activeStrokePoints.isEmpty(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(
                    if (activeTool == InkTool.LASSO) R.string.lasso_hint_draw
                    else R.string.canvas_touch_hint
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
