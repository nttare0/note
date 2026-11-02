package com.example.util

/**
 * Pure, deterministic collection reordering helpers used for drag-and-drop interactions across
 * checklist items, attached images, and workspace items.
 */
object DragDropReorderHelper {

    /**
     * Moves the element at [fromIndex] to [toIndex], returning an updated immutable list.
     * Safely returns the original list when indices are out of bounds or identical.
     */
    fun <T> moveItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (items.size <= 1) return items
        if (fromIndex !in items.indices || toIndex !in items.indices) return items
        if (fromIndex == toIndex) return items

        val mutable = items.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return mutable.toList()
    }

    /**
     * Computes the target index when dragging an item vertically (or horizontally) by [dragOffsetPx]
     * where each slot has an approximate span of [slotSpanPx].
     */
    fun computeTargetIndex(
        startIndex: Int,
        itemCount: Int,
        dragOffsetPx: Float,
        slotSpanPx: Float
    ): Int {
        if (itemCount <= 1 || slotSpanPx <= 1f) return startIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        val deltaSlots = kotlin.math.round(dragOffsetPx / slotSpanPx).toInt()
        return (startIndex + deltaSlots).coerceIn(0, itemCount - 1)
    }
}
