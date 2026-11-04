package com.example.util

import com.example.data.InkStroke
import com.example.data.NoteImageAttachment
import com.example.data.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class LassoBoundsDp(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float, paddingDp: Float = 0f): Boolean {
        return x >= left - paddingDp &&
            x <= right + paddingDp &&
            y >= top - paddingDp &&
            y <= bottom + paddingDp
    }
}

data class LassoHitSelection(
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedImageIds: Set<String> = emptySet()
) {
    val isEmpty: Boolean
        get() = selectedStrokeIds.isEmpty() && selectedImageIds.isEmpty()

    val totalCount: Int
        get() = selectedStrokeIds.size + selectedImageIds.size
}

/**
 * Pure geometric engine for the Samsung Notes-style Lasso Selection tool:
 * - Ray-casting point-in-polygon hit-testing for freehand strokes and canvas-placed images
 * - Axis-aligned bounding box computation for selection handles
 * - 2D rigid + uniform similarity transformation (move, scale, rotate around pivot)
 */
object LassoSelectionEngine {

    /**
     * Standard even-odd ray-casting point-in-polygon test in dp coordinates.
     */
    fun isPointInPolygon(pointX: Float, pointY: Float, polygon: List<StrokePoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y

            val intersects = ((yi > pointY) != (yj > pointY)) &&
                (pointX < (xj - xi) * (pointY - yi) / ((yj - yi).takeIf { abs(it) > 1e-6f } ?: 1e-6f) + xi)
            if (intersects) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Selects strokes and canvas-placed images enclosed or intersected by the user's lasso path.
     * Also supports quick tap-selection on a canvas image or stroke when the lasso gesture is tiny.
     */
    fun findSelectedElements(
        lassoPoints: List<StrokePoint>,
        strokes: List<InkStroke>,
        images: List<NoteImageAttachment>
    ): LassoHitSelection {
        if (lassoPoints.isEmpty()) return LassoHitSelection()

        val minX = lassoPoints.minOf { it.x }
        val maxX = lassoPoints.maxOf { it.x }
        val minY = lassoPoints.minOf { it.y }
        val maxY = lassoPoints.maxOf { it.y }
        val span = hypot(maxX - minX, maxY - minY)

        // Tap-to-select fallback when user taps directly on an image or stroke in Lasso mode
        if (lassoPoints.size < 3 || span < 12f) {
            val tapX = lassoPoints.first().x
            val tapY = lassoPoints.first().y
            return findElementAtPoint(
                xDp = tapX,
                yDp = tapY,
                strokes = strokes,
                images = images,
                hitRadiusDp = 15f
            )
        }

        val hitStrokeIds = strokes.filter { stroke ->
            if (stroke.points.isEmpty()) return@filter false
            val insideCount = stroke.points.count { pt -> isPointInPolygon(pt.x, pt.y, lassoPoints) }
            // Select stroke if at least 45% of its sample points or either endpoint lies inside lasso
            insideCount >= max(1, (stroke.points.size * 0.45f).toInt())
        }.map { it.id }.toSet()

        val hitImageIds = images
            .filter { it.placedOnCanvas }
            .filter { img ->
                val cx = img.canvasXDp + img.canvasWidthDp / 2f
                val cy = img.canvasYDp + img.canvasHeightDp / 2f
                if (isPointInPolygon(cx, cy, lassoPoints)) {
                    true
                } else {
                    val corners = rotatedImageCorners(img)
                    corners.count { (x, y) -> isPointInPolygon(x, y, lassoPoints) } >= 2
                }
            }
            .map { it.id }
            .toSet()

        return LassoHitSelection(
            selectedStrokeIds = hitStrokeIds,
            selectedImageIds = hitImageIds
        )
    }

    /**
     * Computes the axis-aligned bounding box in dp around all currently selected strokes & images.
     */
    fun computeSelectionBounds(
        strokes: List<InkStroke>,
        images: List<NoteImageAttachment>,
        selection: LassoHitSelection
    ): LassoBoundsDp? {
        if (selection.isEmpty) return null

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var hasAny = false

        strokes.forEach { stroke ->
            if (stroke.id in selection.selectedStrokeIds) {
                val hasVarPressure = StrokePressureEngine.hasVariablePressure(
                    points = stroke.points,
                    isStylusStroke = stroke.isStylus
                )
                stroke.points.forEach { pt ->
                    val localW = if (hasVarPressure) {
                        StrokePressureEngine.computePointWidthDp(
                            baseWidthDp = stroke.widthDp,
                            pressure = pt.pressure,
                            isHighlighter = stroke.isHighlighter
                        )
                    } else {
                        stroke.widthDp
                    }
                    val pad = localW / 2f + 4f
                    minX = min(minX, pt.x - pad)
                    minY = min(minY, pt.y - pad)
                    maxX = max(maxX, pt.x + pad)
                    maxY = max(maxY, pt.y + pad)
                    hasAny = true
                }
            }
        }

        images.forEach { img ->
            if (img.placedOnCanvas && img.id in selection.selectedImageIds) {
                rotatedImageCorners(img).forEach { (cx, cy) ->
                    minX = min(minX, cx - 2f)
                    minY = min(minY, cy - 2f)
                    maxX = max(maxX, cx + 2f)
                    maxY = max(maxY, cy + 2f)
                    hasAny = true
                }
            }
        }

        if (!hasAny) return null
        return LassoBoundsDp(
            left = minX,
            top = minY,
            right = max(minX + 24f, maxX),
            bottom = max(minY + 24f, maxY)
        )
    }

    /**
     * Applies translation ([dxDp], [dyDp]), uniform [scaleFactor], and [rotationDeltaDeg] around
     * ([pivotXDp], [pivotYDp]) to all selected strokes.
     */
    fun transformSelectedStrokes(
        strokes: List<InkStroke>,
        selectedStrokeIds: Set<String>,
        pivotXDp: Float,
        pivotYDp: Float,
        dxDp: Float = 0f,
        dyDp: Float = 0f,
        scaleFactor: Float = 1f,
        rotationDeltaDeg: Float = 0f
    ): List<InkStroke> {
        if (selectedStrokeIds.isEmpty()) return strokes
        val safeScale = scaleFactor.coerceIn(0.25f, 4.0f)
        val rad = (rotationDeltaDeg * PI / 180.0).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)

        return strokes.map { stroke ->
            if (stroke.id !in selectedStrokeIds) {
                stroke
            } else {
                val nextPoints = stroke.points.map { pt ->
                    val relX = (pt.x - pivotXDp) * safeScale
                    val relY = (pt.y - pivotYDp) * safeScale
                    val rotX = relX * cosA - relY * sinA
                    val rotY = relX * sinA + relY * cosA
                    pt.copy(
                        x = pivotXDp + rotX + dxDp,
                        y = pivotYDp + rotY + dyDp
                    )
                }
                stroke.copy(
                    points = nextPoints,
                    widthDp = (stroke.widthDp * safeScale).coerceIn(1.0f, 36.0f)
                )
            }
        }
    }

    /**
     * Applies translation ([dxDp], [dyDp]), uniform [scaleFactor], and [rotationDeltaDeg] around
     * ([pivotXDp], [pivotYDp]) to all selected canvas-placed images.
     */
    fun transformSelectedImages(
        images: List<NoteImageAttachment>,
        selectedImageIds: Set<String>,
        pivotXDp: Float,
        pivotYDp: Float,
        dxDp: Float = 0f,
        dyDp: Float = 0f,
        scaleFactor: Float = 1f,
        rotationDeltaDeg: Float = 0f
    ): List<NoteImageAttachment> {
        if (selectedImageIds.isEmpty()) return images
        val safeScale = scaleFactor.coerceIn(0.25f, 4.0f)
        val rad = (rotationDeltaDeg * PI / 180.0).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)

        return images.map { img ->
            if (!img.placedOnCanvas || img.id !in selectedImageIds) {
                img
            } else {
                val centerX = img.canvasXDp + img.canvasWidthDp / 2f
                val centerY = img.canvasYDp + img.canvasHeightDp / 2f
                val relX = (centerX - pivotXDp) * safeScale
                val relY = (centerY - pivotYDp) * safeScale
                val rotX = relX * cosA - relY * sinA
                val rotY = relX * sinA + relY * cosA

                val nextCenterX = pivotXDp + rotX + dxDp
                val nextCenterY = pivotYDp + rotY + dyDp
                val nextW = (img.canvasWidthDp * safeScale).coerceIn(48f, 520f)
                val nextH = (img.canvasHeightDp * safeScale).coerceIn(48f, 520f)
                val nextRot = normalizeDegrees(img.rotationDeg + rotationDeltaDeg)

                img.copy(
                    canvasXDp = nextCenterX - nextW / 2f,
                    canvasYDp = nextCenterY - nextH / 2f,
                    canvasWidthDp = nextW,
                    canvasHeightDp = nextH,
                    rotationDeg = nextRot
                )
            }
        }
    }

    fun rotatedImageCorners(img: NoteImageAttachment): List<Pair<Float, Float>> {
        val cx = img.canvasXDp + img.canvasWidthDp / 2f
        val cy = img.canvasYDp + img.canvasHeightDp / 2f
        val hw = img.canvasWidthDp / 2f
        val hh = img.canvasHeightDp / 2f
        val rad = (img.rotationDeg * PI / 180.0).toFloat()
        val cosA = cos(rad)
        val sinA = sin(rad)

        val localCorners = listOf(
            -hw to -hh,
            hw to -hh,
            hw to hh,
            -hw to hh
        )
        return localCorners.map { (lx, ly) ->
            val rx = cx + lx * cosA - ly * sinA
            val ry = cy + lx * sinA + ly * cosA
            rx to ry
        }
    }

    private fun isPointInsideRotatedImage(
        px: Float,
        py: Float,
        img: NoteImageAttachment,
        paddingDp: Float = 0f
    ): Boolean {
        val cx = img.canvasXDp + img.canvasWidthDp / 2f
        val cy = img.canvasYDp + img.canvasHeightDp / 2f
        val rad = (-img.rotationDeg * PI / 180.0).toFloat()
        val dx = px - cx
        val dy = py - cy
        val localX = dx * cos(rad) - dy * sin(rad)
        val localY = dx * sin(rad) + dy * cos(rad)
        val hw = img.canvasWidthDp / 2f + paddingDp
        val hh = img.canvasHeightDp / 2f + paddingDp
        return abs(localX) <= hw && abs(localY) <= hh
    }

    /**
     * Hit-tests the topmost canvas-placed image or handwriting stroke at ([xDp], [yDp])
     * using exact point-to-segment distance so long-press or tap anywhere along a stroke selects it.
     */
    fun findElementAtPoint(
        xDp: Float,
        yDp: Float,
        strokes: List<InkStroke>,
        images: List<NoteImageAttachment>,
        hitRadiusDp: Float = 16f
    ): LassoHitSelection {
        val hitImage = images
            .filter { it.placedOnCanvas }
            .lastOrNull { img -> isPointInsideRotatedImage(xDp, yDp, img, paddingDp = 6f) }
        if (hitImage != null) {
            return LassoHitSelection(selectedImageIds = setOf(hitImage.id))
        }

        val hitStroke = strokes.lastOrNull { stroke ->
            if (stroke.points.isEmpty()) return@lastOrNull false
            val threshold = max(hitRadiusDp, stroke.widthDp * 1.8f)
            if (stroke.points.size == 1) {
                val pt = stroke.points.first()
                hypot(pt.x - xDp, pt.y - yDp) <= threshold
            } else {
                var hit = false
                for (i in 0 until stroke.points.size - 1) {
                    val p0 = stroke.points[i]
                    val p1 = stroke.points[i + 1]
                    if (pointToSegmentDistanceDp(xDp, yDp, p0.x, p0.y, p1.x, p1.y) <= threshold) {
                        hit = true
                        break
                    }
                }
                hit
            }
        }
        return if (hitStroke != null) {
            LassoHitSelection(selectedStrokeIds = setOf(hitStroke.id))
        } else {
            LassoHitSelection()
        }
    }

    private fun pointToSegmentDistanceDp(
        px: Float,
        py: Float,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ): Float {
        val vx = x1 - x0
        val vy = y1 - y0
        val lenSq = vx * vx + vy * vy
        if (lenSq < 1e-6f) return hypot(px - x0, py - y0)
        val t = (((px - x0) * vx + (py - y0) * vy) / lenSq).coerceIn(0f, 1f)
        val projX = x0 + t * vx
        val projY = y0 + t * vy
        return hypot(px - projX, py - projY)
    }

    private fun normalizeDegrees(deg: Float): Float {
        var angle = deg % 360f
        if (angle > 180f) angle -= 360f
        if (angle < -180f) angle += 360f
        return angle
    }
}
