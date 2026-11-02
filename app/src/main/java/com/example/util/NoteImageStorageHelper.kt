package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.NoteImageAttachment
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies user-picked images from the zero-permission Android Photo Picker (`PickVisualMedia`)
 * into internal app storage (`filesDir/note_images/`) so images remain permanently readable across
 * app restarts and reboots without needing broad storage permissions.
 */
object NoteImageStorageHelper {

    suspend fun importPickedImage(context: Context, sourceUri: Uri): NoteImageAttachment? =
        withContext(Dispatchers.IO) {
            try {
                val imagesDir = File(context.filesDir, "note_images").apply { mkdirs() }
                val attachmentId = UUID.randomUUID().toString()
                val destFile = File(imagesDir, "img_$attachmentId.jpg")

                // Decode with safe downsampling if image is huge (>1600px) so notes scroll smoothly
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, boundsOptions)
                }

                val maxDim = 1600
                var sampleSize = 1
                while (
                    boundsOptions.outWidth / sampleSize > maxDim ||
                    boundsOptions.outHeight / sampleSize > maxDim
                ) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap: Bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOptions)
                } ?: return@withContext null

                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }

                val w = bitmap.width
                val h = bitmap.height
                bitmap.recycle()

                NoteImageAttachment(
                    id = attachmentId,
                    localPath = destFile.absolutePath,
                    widthPx = w,
                    heightPx = h
                )
            } catch (_: Exception) {
                null
            }
        }

    suspend fun deleteLocalImage(localPath: String) = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(localPath)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
