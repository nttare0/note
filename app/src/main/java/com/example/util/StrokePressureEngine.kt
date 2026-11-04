package com.example.util

import com.example.data.StrokePoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Sampled spine node in density-independent pixels (`dp`) carrying position and local half-width radius.
 */
data class SpineSampleDp(
    val xDp: Float,
    val yDp: Float,
    val radiusDp: Float
)

/**
 * Clockwise-wound quadrilateral bridging the external bitangents of two circles in screen coordinates
 * (`+x` right, `+y` down), suitable for `NonZero` / `WINDING` union path construction without alpha beading.
 */
data class BitangentQuad(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val x3: Float,
    val y3: Float
)

/**
 * Pure stylus pressure signal processor and variable-width stroke geometry engine.
 *
 * - Normalizes hardware `MotionEvent` / `PointerInputChange` stylus pressure across digitizer ranges.
 * - Applies low-latency causal smoothing during live capture and symmetric 3-tap smoothing on curves.
 * - Maps stylus pressure through a calligraphic S-Pen response curve (`0.35x .. 1.65x` nominal width).
 * - Evaluates midpoint-quadratic Bezier spines with interpolated radii and analytical external bitangents.
 */
object StrokePressureEngine {

    const val MIN_PRESSURE_VALUE = 0.05f
    const val MAX_PRESSURE_VALUE = 1.35f
    const val NEUTRAL_NON_STYLUS_PRESSURE = 1.0f

    const val MIN_PEN_WIDTH_SCALE = 0.34f
    const val MAX_PEN_WIDTH_SCALE = 1.66f

    const val MIN_MARKER_WIDTH_SCALE = 0.80f
    const val MAX_MARKER_WIDTH_SCALE = 1.22f

    /**
     * Normalizes raw hardware stylus pressure against the input device's reported `[minPressure, maxPressure]`
     * axis range while gracefully preserving firm over-presses up to [MAX_PRESSURE_VALUE].
     */
    fun normalizeStylusPressure(
        rawPressure: Float,
        minPressure: Float = 0f,
        maxPressure: Float = 1f
    ): Float {
        if (!rawPressure.isFinite() || rawPressure <= 0f) return MIN_PRESSURE_VALUE
        val span = (maxPressure - minPressure).takeIf { it > 1e-4f } ?: 1f
        val mapped = (rawPressure - minPressure) / span
        return mapped.coerceIn(MIN_PRESSURE_VALUE, MAX_PRESSURE_VALUE)
    }

    /**
     * Causal exponential moving average used during live pointer input to suppress high-frequency
     * digitizer ADC jitter without introducing perceptible stylus lag.
     */
    fun smoothNextPressure(
        previousSmoothed: Float,
        targetNormalized: Float,
        smoothingFactor: Float = 0.48f
    ): Float {
        val alpha = smoothingFactor.coerceIn(0.05f, 1.0f)
        val clampedTarget = targetNormalized.coerceIn(MIN_PRESSURE_VALUE, MAX_PRESSURE_VALUE)
        return (previousSmoothed + (clampedTarget - previousSmoothed) * alpha)
            .coerceIn(MIN_PRESSURE_VALUE, MAX_PRESSURE_VALUE)
    }

    /**
     * Determines whether a stroke carries stylus or per-point pressure variation.
     * Non-stylus strokes recorded with uniform default `1.0f` pressure stay at exact `1.0x` nominal width.
     */
    fun hasVariablePressure(
        points: List<StrokePoint>,
        isStylusStroke: Boolean = false
    ): Boolean {
        if (points.isEmpty()) return false
        if (isStylusStroke) return true
        var minP = Float.POSITIVE_INFINITY
        var maxP = Float.NEGATIVE_INFINITY
        for (pt in points) {
            if (abs(pt.pressure - NEUTRAL_NON_STYLUS_PRESSURE) > 0.015f) {
                return true
            }
            if (pt.pressure < minP) minP = pt.pressure
            if (pt.pressure > maxP) maxP = pt.pressure
        }
        return (maxP - minP) > 0.015f
    }

    /**
     * Maps normalized stylus pressure (`0.05..1.35`) to a stroke thickness multiplier.
     * Normal comfortable handwriting force (`~0.58`) sits right around `1.0x` nominal width,
     * feather-light touches taper down to `~0.37x`, and firm presses swell up to `~1.62x`.
     */
    fun pressureToWidthScale(
        pressure: Float,
        isHighlighter: Boolean = false
    ): Float {
        val clamped = pressure.coerceIn(MIN_PRESSURE_VALUE, MAX_PRESSURE_VALUE)
        val curved = clamped.pow(0.82f)
        return if (isHighlighter) {
            (0.82f + 0.30f * curved).coerceIn(MIN_MARKER_WIDTH_SCALE, MAX_MARKER_WIDTH_SCALE)
        } else {
            (0.28f + 1.08f * curved).coerceIn(MIN_PEN_WIDTH_SCALE, MAX_PEN_WIDTH_SCALE)
        }
    }

    /**
     * Computes the effective stroke thickness in `dp` at a single sample point.
     */
    fun computePointWidthDp(
        baseWidthDp: Float,
        pressure: Float,
        isHighlighter: Boolean = false,
        pressureEnabled: Boolean = true
    ): Float {
        val safeBase = baseWidthDp.coerceAtLeast(0.6f)
        if (!pressureEnabled) return safeBase
        val scale = pressureToWidthScale(pressure, isHighlighter)
        return (safeBase * scale).coerceIn(0.55f, 48.0f)
    }

    /**
     * Applies a gentle 3-tap filter on dense interior samples while strictly preserving exact
     * entry/exit stylus taper pressures and short-stroke peaks.
     */
    fun smoothPointPressures(points: List<StrokePoint>): FloatArray {
        val n = points.size
        val out = FloatArray(n)
        if (n == 0) return out
        for (i in 0 until n) {
            out[i] = points[i].pressure.coerceIn(MIN_PRESSURE_VALUE, MAX_PRESSURE_VALUE)
        }
        if (n < 5) return out

        val rawCopy = out.copyOf()
        for (i in 1 until n - 1) {
            val blended = rawCopy[i - 1] * 0.14f +
                rawCopy[i] * 0.72f +
                rawCopy[i + 1] * 0.14f
            out[i] = blended.coerceIn(MIN_PRESSURE_VALUE, MAX_PRESSURE_VALUE)
        }
        return out
    }

    private fun smoothInterpolateRadius(rStart: Float, rCtrl: Float, rEnd: Float, t: Float): Float {
        return if (t <= 0.5f) {
            val localT = (t * 2f).coerceIn(0f, 1f)
            val eased = localT * localT * (3f - 2f * localT)
            rStart + (rCtrl - rStart) * eased
        } else {
            val localT = ((t - 0.5f) * 2f).coerceIn(0f, 1f)
            val eased = localT * localT * (3f - 2f * localT)
            rCtrl + (rEnd - rCtrl) * eased
        }
    }

    /**
     * Evaluates a midpoint-quadratic Bezier spine with smoothly interpolated per-sample radii in `dp`
     * that pass faithfully through every stylus pressure peak and entry/exit taper.
     *
     * @param extraWidthDp Optional uniform padding added to width (e.g., `+5dp` for Lasso selection halos).
     */
    fun buildVariableWidthSpine(
        points: List<StrokePoint>,
        baseWidthDp: Float,
        isHighlighter: Boolean = false,
        extraWidthDp: Float = 0f,
        maxStepDp: Float = 2.0f
    ): List<SpineSampleDp> {
        if (points.isEmpty()) return emptyList()

        val smoothedPressures = smoothPointPressures(points)
        val pointRadii = FloatArray(points.size) { idx ->
            val w = computePointWidthDp(
                baseWidthDp = baseWidthDp,
                pressure = smoothedPressures[idx],
                isHighlighter = isHighlighter,
                pressureEnabled = true
            ) + extraWidthDp
            max(0.28f, w * 0.5f)
        }

        if (points.size == 1) {
            return listOf(
                SpineSampleDp(
                    xDp = points[0].x,
                    yDp = points[0].y,
                    radiusDp = pointRadii[0]
                )
            )
        }

        val stepDp = maxStepDp.coerceAtLeast(0.6f)
        val samples = ArrayList<SpineSampleDp>(points.size * 3)

        if (points.size == 2) {
            val p0 = points[0]
            val p1 = points[1]
            val r0 = pointRadii[0]
            val r1 = pointRadii[1]
            val dist = hypot(p1.x - p0.x, p1.y - p0.y)
            val steps = ceil(dist / stepDp).toInt().coerceIn(1, 48)
            for (s in 0..steps) {
                val t = s.toFloat() / steps.toFloat()
                samples.add(
                    SpineSampleDp(
                        xDp = p0.x + (p1.x - p0.x) * t,
                        yDp = p0.y + (p1.y - p0.y) * t,
                        radiusDp = r0 + (r1 - r0) * t
                    )
                )
            }
            return samples
        }

        // Start from p0 -> midpoint(p0, p1) so the initial segment joins seamlessly into the quadratic chain
        var startX = points[0].x
        var startY = points[0].y
        var startR = pointRadii[0]
        samples.add(SpineSampleDp(startX, startY, startR))

        val firstMidX = (points[0].x + points[1].x) * 0.5f
        val firstMidY = (points[0].y + points[1].y) * 0.5f
        val firstMidR = (pointRadii[0] + pointRadii[1]) * 0.5f
        appendLinearSpan(samples, startX, startY, startR, firstMidX, firstMidY, firstMidR, stepDp)
        startX = firstMidX
        startY = firstMidY
        startR = firstMidR

        for (i in 1 until points.size - 1) {
            val ctrlX = points[i].x
            val ctrlY = points[i].y
            val ctrlR = pointRadii[i]

            val endX: Float
            val endY: Float
            val endR: Float
            if (i == points.size - 2) {
                endX = points[i + 1].x
                endY = points[i + 1].y
                endR = pointRadii[i + 1]
            } else {
                endX = (ctrlX + points[i + 1].x) * 0.5f
                endY = (ctrlY + points[i + 1].y) * 0.5f
                endR = (ctrlR + pointRadii[i + 1]) * 0.5f
            }

            val approxLen = hypot(ctrlX - startX, ctrlY - startY) + hypot(endX - ctrlX, endY - ctrlY)
            val steps = ceil(approxLen / stepDp).toInt().coerceIn(2, 32)
            for (s in 1..steps) {
                val t = s.toFloat() / steps.toFloat()
                val u = 1f - t
                val uu = u * u
                val twoUt = 2f * u * t
                val tt = t * t
                samples.add(
                    SpineSampleDp(
                        xDp = uu * startX + twoUt * ctrlX + tt * endX,
                        yDp = uu * startY + twoUt * ctrlY + tt * endY,
                        radiusDp = smoothInterpolateRadius(startR, ctrlR, endR, t)
                    )
                )
            }
            startX = endX
            startY = endY
            startR = endR
        }

        return samples
    }

    private fun appendLinearSpan(
        out: MutableList<SpineSampleDp>,
        x0: Float,
        y0: Float,
        r0: Float,
        x1: Float,
        y1: Float,
        r1: Float,
        stepDp: Float
    ) {
        val dist = hypot(x1 - x0, y1 - y0)
        val steps = ceil(dist / stepDp).toInt().coerceIn(1, 24)
        for (s in 1..steps) {
            val t = s.toFloat() / steps.toFloat()
            out.add(
                SpineSampleDp(
                    xDp = x0 + (x1 - x0) * t,
                    yDp = y0 + (y1 - y0) * t,
                    radiusDp = r0 + (r1 - r0) * t
                )
            )
        }
    }

    /**
     * Computes the clockwise-wound external bitangent quadrilateral connecting circle `(x0, y0, r0)`
     * and circle `(x1, y1, r1)` in screen coordinates (`+x` right, `+y` down).
     * Returns `null` when one circle completely encloses the other (`|r0 - r1| >= d`).
     */
    fun computeBitangentQuad(
        x0: Float,
        y0: Float,
        r0: Float,
        x1: Float,
        y1: Float,
        r1: Float
    ): BitangentQuad? {
        val dx = x1 - x0
        val dy = y1 - y0
        val dist = hypot(dx, dy)
        if (dist <= 1e-3f || abs(r0 - r1) >= dist * 0.995f) {
            return null
        }

        val ux = dx / dist
        val uy = dy / dist
        // Screen-space left-of-ray normal (points upward (0, -1) when ray points right (1, 0))
        val lx = uy
        val ly = -ux

        val sinAlpha = ((r0 - r1) / dist).coerceIn(-0.985f, 0.985f)
        val cosAlpha = sqrt((1f - sinAlpha * sinAlpha).coerceAtLeast(0.02f))

        val topNx = ux * sinAlpha + lx * cosAlpha
        val topNy = uy * sinAlpha + ly * cosAlpha
        val botNx = ux * sinAlpha - lx * cosAlpha
        val botNy = uy * sinAlpha - ly * cosAlpha

        // Clockwise order in screen coordinates (+x right, +y down):
        // top-start -> top-end -> bottom-end -> bottom-start
        return BitangentQuad(
            x0 = x0 + topNx * r0,
            y0 = y0 + topNy * r0,
            x1 = x1 + topNx * r1,
            y1 = y1 + topNy * r1,
            x2 = x1 + botNx * r1,
            y2 = y1 + botNy * r1,
            x3 = x0 + botNx * r0,
            y3 = y0 + botNy * r0
        )
    }
}
