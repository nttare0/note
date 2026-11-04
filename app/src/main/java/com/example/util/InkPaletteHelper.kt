package com.example.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure utility for NoteStudio's customizable handwriting color palette:
 * - Normalizes and validates "#RRGGBB" custom ink keys
 * - Converts "#RRGGBB" to ARGB Int without platform dependencies so unit tests and Canvas share logic
 * - Converts between HSV (Hue 0..360, Saturation 0..1, Value 0..1) and "#RRGGBB" for the minimal color picker
 */
object InkPaletteHelper {

    val DEFAULT_PALETTE_KEYS: List<String> = listOf(
        "INK",
        "AMBER",
        "SLATE",
        "BLUE",
        "GREEN",
        "CORAL"
    )

    /**
     * Curated Excalidraw / Open-Color inspired ink presets for one-tap custom palette additions.
     */
    val CURATED_PRESET_HEX_COLORS: List<String> = listOf(
        "#7048E8", // Deep Violet
        "#4263EB", // Indigo Ink
        "#1098AD", // Cyan Ink
        "#0CA678", // Emerald Teal
        "#5C940D", // Olive Leaf
        "#E67700", // Warm Ochre
        "#F76707", // Tangerine
        "#D6336C", // Rose Berry
        "#AE3EC9", // Orchid Plum
        "#C92A2A", // Deep Crimson
        "#495057", // Charcoal Slate
        "#8D5524"  // Sepia Brown
    )

    /**
     * Normalizes user-supplied hex strings ("7048e8", "#7048E8", "#74E") into uppercase "#RRGGBB".
     * Returns null if [rawInput] is not a valid 3-digit or 6-digit hex RGB color.
     */
    fun normalizeHexColorKey(rawInput: String): String? {
        val cleaned = rawInput.trim().removePrefix("#").uppercase()
        val expanded = when (cleaned.length) {
            3 -> buildString(6) {
                cleaned.forEach { ch ->
                    append(ch)
                    append(ch)
                }
            }
            6 -> cleaned
            else -> return null
        }
        val allHex = expanded.all { ch ->
            ch in '0'..'9' || ch in 'A'..'F'
        }
        return if (allHex) "#$expanded" else null
    }

    fun isCustomHexKey(colorKey: String): Boolean {
        return colorKey.startsWith("#") && normalizeHexColorKey(colorKey) != null
    }

    /**
     * Parses a "#RRGGBB" color key into an opaque ARGB 32-bit integer (0xFFRRGGBB).
     * Returns null for named semantic keys ("INK", "AMBER", etc.) or malformed strings.
     */
    fun parseHexRgbToArgbInt(colorKey: String): Int? {
        if (!colorKey.startsWith("#")) return null
        val normalized = normalizeHexColorKey(colorKey) ?: return null
        val rgb = normalized.substring(1).toIntOrNull(16) ?: return null
        return (0xFF shl 24) or (rgb and 0xFFFFFF)
    }

    /**
     * Converts HSV components (hueDeg in 0..360, saturation in 0..1, value in 0..1)
     * into a normalized "#RRGGBB" string.
     */
    fun hsvToHexKey(hueDeg: Float, saturation: Float, value: Float): String {
        val h = ((hueDeg % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val c = v * s
        val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
        val m = v - c

        val (rPrime, gPrime, bPrime) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((rPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((gPrime + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((bPrime + m) * 255f).roundToInt().coerceIn(0, 255)

        return String.format("#%02X%02X%02X", r, g, b)
    }

    /**
     * Converts a "#RRGGBB" key into Triple(hueDeg, saturation, value), or null if invalid.
     */
    fun hexKeyToHsv(colorKey: String): Triple<Float, Float, Float>? {
        val argb = parseHexRgbToArgbInt(colorKey) ?: return null
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC

        val hue = when {
            delta < 1e-5f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val sat = if (maxC < 1e-5f) 0f else (delta / maxC).coerceIn(0f, 1f)
        val value = maxC.coerceIn(0f, 1f)
        return Triple(hue, sat, value)
    }
}
