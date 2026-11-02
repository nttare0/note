package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object NoteSerializer {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val strokeListType = Types.newParameterizedType(List::class.java, InkStroke::class.java)
    private val strokeAdapter = moshi.adapter<List<InkStroke>>(strokeListType)

    private val checklistType = Types.newParameterizedType(List::class.java, ChecklistItem::class.java)
    private val checklistAdapter = moshi.adapter<List<ChecklistItem>>(checklistType)

    private val imageListType = Types.newParameterizedType(List::class.java, NoteImageAttachment::class.java)
    private val imageAdapter = moshi.adapter<List<NoteImageAttachment>>(imageListType)

    fun encodeStrokes(strokes: List<InkStroke>): String {
        if (strokes.isEmpty()) return "[]"
        return strokeAdapter.toJson(strokes)
    }

    fun decodeStrokes(json: String): List<InkStroke> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            strokeAdapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeChecklist(items: List<ChecklistItem>): String {
        if (items.isEmpty()) return "[]"
        return checklistAdapter.toJson(items)
    }

    fun decodeChecklist(json: String): List<ChecklistItem> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            checklistAdapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeImages(images: List<NoteImageAttachment>): String {
        if (images.isEmpty()) return "[]"
        return imageAdapter.toJson(images)
    }

    fun decodeImages(json: String): List<NoteImageAttachment> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            imageAdapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
