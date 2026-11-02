package com.example.util

import com.example.data.InkStroke
import com.example.data.StrokePoint
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Pure geometric engine for Samsung Notes-style segment erasing.
 *
 * Instead of deleting an entire stroke when touched, this utility:
 * 1. Densifies long straight segments so fast pen strokes don't let the eraser pass between vertices.
 * 2. Tests each point/segment against the swept eraser capsule from (fromXDp, fromYDp) -> (toXDp, toYDp)
 *    using point-to-segment distance.
 * 3. Splits partially erased strokes into clean surviving sub-strokes so users can erase small gaps
 *    inside lines, handwriting loops, or diagrams.
 */
object StrokeEraserEngine {

    data class ErasePassResult(
        val updatedStrokes: List<InkStroke>,
        val modified: Boolean
    )

    fun eraseSegmentsAlongSweep(
        strokes: List<InkStroke>,
        fromXDp: Float,
        fromYDp: Float,
        toXDp: Float,
        toYDp: Float,
        eraserRadiusDp: Float
    ): ErasePassResult {
        if (strokes.isEmpty()) return ErasePassResult(strokes, false)

        var anyModified = false
        val result = ArrayList<InkStroke>(strokes.size)

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) {
                anyModified = true
                continue
            }

            val effectiveRadius = eraserRadiusDp + (stroke.widthDp * 0.45f)
            val radiusSq = effectiveRadius * effectiveRadius

            // Fast bounding-box rejection before densifying points
            if (!sweepIntersectsBoundingBox(stroke.points, fromXDp, fromYDp, toXDp, toYDp, effectiveRadius)) {
                result.add(stroke)
                continue
            }

            // Densify sparse segments (max 3.5dp step) so erasing cleanly carves straight lines
            val densePoints = densifyPoints(stroke.points, maxStepDp = 3.5f)
            var strokeHit = false
            val survivingRuns = mutableListOf<MutableList<StrokePoint>>()
            var currentRun: MutableList<StrokePoint>? = null

            for (pt in densePoints) {
                val distSq = distancePointToSegmentSq(
                    px = pt.x,
                    py = pt.y,
                    ax = fromXDp,
                    ay = fromYDp,
                    bx = toXDp,
                    by = toYDp
                )
                if (distSq <= radiusSq) {
                    strokeHit = true
                    currentRun = null
                } else {
                    if (currentRun == null) {
                        val fresh = mutableListOf<StrokePoint>()
                        survivingRuns.add(fresh)
                        currentRun = fresh
                    }
                    currentRun.add(pt)
                }
            }

            if (!strokeHit) {
                result.add(stroke)
            } else {
                anyModified = true
                for (run in survivingRuns) {
                    if (run.isNotEmpty()) {
                        result.add(
                            stroke.copy(
                                id = UUID.randomUUID().toString(),
                                points = run
                            )
                        )
                    }
                }
            }
        }

        return if (anyModified) {
            ErasePassResult(result, true)
        } else {
            ErasePassResult(strokes, false)
        }
    }

    private fun sweepIntersectsBoundingBox(
        points: List<StrokePoint>,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        pad: Float
    ): Boolean {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val sweepMinX = minOf(ax, bx) - pad
        val sweepMaxX = maxOf(ax, bx) + pad
        val sweepMinY = minOf(ay, by) - pad
        val sweepMaxY = maxOf(ay, by) + pad

        return maxX >= sweepMinX && minX <= sweepMaxX && maxY >= sweepMinY && minY <= sweepMaxY
    }

    private fun densifyPoints(points: List<StrokePoint>, maxStepDp: Float): List<StrokePoint> {
        if (points.size <= 1) return points
        val output = ArrayList<StrokePoint>(points.size * 2)
        output.add(points.first())

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val dist = hypot(curr.x - prev.x, curr.y - prev.y)
            if (dist > maxStepDp) {
                val steps = ceil(dist / maxStepDp).toInt().coerceAtLeast(2)
                for (s in 1 until steps) {
                    val t = s.toFloat() / steps.toFloat()
                    output.add(
                        StrokePoint(
                            x = prev.x + (curr.x - prev.x) * t,
                            y = prev.y + (curr.y - prev.y) * t,
                            pressure = prev.pressure + (curr.pressure - prev.pressure) * t
                        )
                    )
                }
            }
            output.add(curr)
        }
        return output
    }

    fun distancePointToSegmentSq(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {
        val abx = bx - ax
        val aby = by - ay
        val lenSq = abx * abx + aby * aby
        if (lenSq <= 1e-6f) {
            val dx = px - ax
            val dy = py - ay
            return dx * dx + dy * dy
        }
        val t = (((px - ax) * abx + (py - ay) * aby) / lenSq).coerceIn(0f, 1f)
        val projX = ax + t * abx
        val projY = ay + t * aby
        val dx = px - projX
        val dy = py - projY
        return dx * dx + dy * dy
    }
}
