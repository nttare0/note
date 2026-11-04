package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.InkStroke
import com.example.data.NoteImageAttachment
import com.example.data.PaperTemplate
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the current drawing canvas (paper template + smoothed vector ink strokes) into a
 * high-resolution PNG bitmap and saves it directly to the device Gallery via `MediaStore.Images`.
 */
object CanvasPngExportHelper {

    fun renderCanvasToBitmap(
        strokes: List<InkStroke>,
        template: PaperTemplate,
        isDarkSurface: Boolean,
        canvasImages: List<NoteImageAttachment> = emptyList(),
        scalePxPerDp: Float = 3.0f
    ): Bitmap {
        val placedImages = canvasImages.filter { it.placedOnCanvas }
        // Compute dynamic canvas bounds in dp so long or wide sketches and canvas images are never clipped
        val maxStrokeX = strokes.flatMap { it.points }.maxOfOrNull { it.x } ?: 0f
        val maxStrokeY = strokes.flatMap { it.points }.maxOfOrNull { it.y } ?: 0f
        val maxImgX = placedImages.maxOfOrNull { it.canvasXDp + it.canvasWidthDp } ?: 0f
        val maxImgY = placedImages.maxOfOrNull { it.canvasYDp + it.canvasHeightDp } ?: 0f

        val widthDp = max(380f, max(maxStrokeX, maxImgX) + 48f)
        val heightDp = max(480f, max(maxStrokeY, maxImgY) + 56f)

        val widthPx = (widthDp * scalePxPerDp).toInt().coerceIn(600, 3200)
        val heightPx = (heightDp * scalePxPerDp).toInt().coerceIn(720, 4096)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Fill page background (Excalidraw Dark Tone Ink #121212 or crisp Whiteboard #FFFFFF)
        val bgHex = if (isDarkSurface) "#121212" else "#FFFFFF"
        canvas.drawColor(Color.parseColor(bgHex))

        // 2. Render active paper template lines/dots
        drawPaperTemplateOnCanvas(
            canvas = canvas,
            widthPx = widthPx.toFloat(),
            heightPx = heightPx.toFloat(),
            template = template,
            isDarkSurface = isDarkSurface,
            scale = scalePxPerDp
        )

        // 2.5 Render canvas-placed images (supporting Lasso move, scale, and rotation)
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        placedImages.forEach { attachment ->
            val file = File(attachment.localPath)
            if (file.exists()) {
                val decoded = BitmapFactory.decodeFile(file.absolutePath)
                if (decoded != null) {
                    val leftPx = attachment.canvasXDp * scalePxPerDp
                    val topPx = attachment.canvasYDp * scalePxPerDp
                    val rightPx = (attachment.canvasXDp + attachment.canvasWidthDp) * scalePxPerDp
                    val bottomPx = (attachment.canvasYDp + attachment.canvasHeightDp) * scalePxPerDp
                    val cx = (leftPx + rightPx) / 2f
                    val cy = (topPx + bottomPx) / 2f
                    canvas.save()
                    canvas.rotate(attachment.rotationDeg, cx, cy)
                    canvas.drawBitmap(decoded, null, RectF(leftPx, topPx, rightPx, bottomPx), imgPaint)
                    canvas.restore()
                    decoded.recycle()
                }
            }
        }

        // 3. Render smoothed quadratic Bezier strokes (supporting stylus pressure variable width)
        strokes.forEach { stroke ->
            val pts = stroke.points
            if (pts.isNotEmpty()) {
                val strokeColor = resolveExportInkColor(
                    colorKey = stroke.colorKey,
                    isHighlighter = stroke.isHighlighter,
                    isDarkSurface = isDarkSurface
                )
                val useVariablePressure = StrokePressureEngine.hasVariablePressure(
                    points = pts,
                    isStylusStroke = stroke.isStylus
                )

                if (useVariablePressure) {
                    val spine = StrokePressureEngine.buildVariableWidthSpine(
                        points = pts,
                        baseWidthDp = stroke.widthDp,
                        isHighlighter = stroke.isHighlighter,
                        maxStepDp = 1.8f
                    )
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = strokeColor
                        style = Paint.Style.FILL
                    }
                    if (spine.size == 1) {
                        canvas.drawCircle(
                            spine[0].xDp * scalePxPerDp,
                            spine[0].yDp * scalePxPerDp,
                            spine[0].radiusDp * scalePxPerDp,
                            fillPaint
                        )
                    } else if (spine.size > 1) {
                        val ribbonPath = Path().apply {
                            fillType = Path.FillType.WINDING
                            var prevX = spine[0].xDp * scalePxPerDp
                            var prevY = spine[0].yDp * scalePxPerDp
                            var prevR = spine[0].radiusDp * scalePxPerDp
                            addCircle(prevX, prevY, prevR, Path.Direction.CW)

                            for (i in 1 until spine.size) {
                                val curX = spine[i].xDp * scalePxPerDp
                                val curY = spine[i].yDp * scalePxPerDp
                                val curR = spine[i].radiusDp * scalePxPerDp
                                addCircle(curX, curY, curR, Path.Direction.CW)

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
                        canvas.drawPath(ribbonPath, fillPaint)
                    }
                } else if (pts.size == 1) {
                    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = strokeColor
                        style = Paint.Style.FILL
                    }
                    canvas.drawCircle(
                        pts[0].x * scalePxPerDp,
                        pts[0].y * scalePxPerDp,
                        (stroke.widthDp * scalePxPerDp) / 2f,
                        dotPaint
                    )
                } else {
                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = strokeColor
                        style = Paint.Style.STROKE
                        strokeWidth = stroke.widthDp * scalePxPerDp
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    val path = Path()
                    path.moveTo(pts[0].x * scalePxPerDp, pts[0].y * scalePxPerDp)
                    if (pts.size == 2) {
                        path.lineTo(pts[1].x * scalePxPerDp, pts[1].y * scalePxPerDp)
                    } else {
                        for (i in 1 until pts.size - 1) {
                            val ctrlX = pts[i].x * scalePxPerDp
                            val ctrlY = pts[i].y * scalePxPerDp
                            val nextX = pts[i + 1].x * scalePxPerDp
                            val nextY = pts[i + 1].y * scalePxPerDp
                            val midX = (ctrlX + nextX) / 2f
                            val midY = (ctrlY + nextY) / 2f
                            path.quadTo(ctrlX, ctrlY, midX, midY)
                        }
                        val last = pts.last()
                        path.lineTo(last.x * scalePxPerDp, last.y * scalePxPerDp)
                    }
                    canvas.drawPath(path, strokePaint)
                }
            }
        }

        return bitmap
    }

    suspend fun saveCanvasPngToGallery(
        context: Context,
        noteTitle: String,
        strokes: List<InkStroke>,
        template: PaperTemplate,
        isDarkSurface: Boolean,
        canvasImages: List<NoteImageAttachment> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = renderCanvasToBitmap(
                strokes = strokes,
                template = template,
                isDarkSurface = isDarkSurface,
                canvasImages = canvasImages
            )

            val cleanBase = noteTitle
                .trim()
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .take(28)
                .ifBlank { "notestudio_canvas" }
            val fileName = "${cleanBase}_${System.currentTimeMillis()}.png"

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.WIDTH, bitmap.width)
                put(MediaStore.Images.Media.HEIGHT, bitmap.height)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/NoteStudio"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val imageUri = resolver.insert(collection, contentValues) ?: run {
                bitmap.recycle()
                return@withContext false
            }

            val wroteSuccessfully = resolver.openOutputStream(imageUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false

            bitmap.recycle()

            if (!wroteSuccessfully) {
                resolver.delete(imageUri, null, null)
                return@withContext false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(imageUri, publishValues, null, null)
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    private fun drawPaperTemplateOnCanvas(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        template: PaperTemplate,
        isDarkSurface: Boolean,
        scale: Float
    ) {
        val spacingPx = 28f * scale
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (isDarkSurface) "#292833" else "#ECEBF4")
            strokeWidth = 1f * scale
            style = Paint.Style.STROKE
        }
        val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (isDarkSurface) "#3D3B4F" else "#D5D3E6")
            strokeWidth = 1.5f * scale
            style = Paint.Style.STROKE
        }

        when (template) {
            PaperTemplate.BLANK -> Unit
            PaperTemplate.RULED -> {
                var y = spacingPx
                while (y < heightPx) {
                    canvas.drawLine(0f, y, widthPx, y, linePaint)
                    y += spacingPx
                }
            }
            PaperTemplate.GRID -> {
                var colIndex = 1
                var x = spacingPx
                while (x < widthPx) {
                    val paint = if (colIndex % 4 == 0) marginPaint else linePaint
                    canvas.drawLine(x, 0f, x, heightPx, paint)
                    x += spacingPx
                    colIndex++
                }
                var rowIndex = 1
                var y = spacingPx
                while (y < heightPx) {
                    val paint = if (rowIndex % 4 == 0) marginPaint else linePaint
                    canvas.drawLine(0f, y, widthPx, y, paint)
                    y += spacingPx
                    rowIndex++
                }
            }
            PaperTemplate.DOTTED -> {
                val dotSpacing = 24f * scale
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(if (isDarkSurface) "#52525B" else "#A1A1AA")
                    style = Paint.Style.FILL
                }
                var x = dotSpacing
                while (x < widthPx) {
                    var y = dotSpacing
                    while (y < heightPx) {
                        canvas.drawCircle(x, y, 1.3f * scale, dotPaint)
                        y += dotSpacing
                    }
                    x += dotSpacing
                }
            }
            PaperTemplate.CORNELL -> {
                val summaryTop = (heightPx - 72f * scale).coerceAtLeast(spacingPx * 3f)
                var y = spacingPx * 1.5f
                while (y < summaryTop) {
                    canvas.drawLine(0f, y, widthPx, y, linePaint)
                    y += spacingPx
                }
                val cueX = (widthPx * 0.28f).coerceAtMost(130f * scale)
                canvas.drawLine(cueX, 0f, cueX, summaryTop, marginPaint)
                canvas.drawLine(0f, summaryTop, widthPx, summaryTop, marginPaint)
            }
        }
    }

    private fun resolveExportInkColor(
        colorKey: String,
        isHighlighter: Boolean,
        isDarkSurface: Boolean
    ): Int {
        val base = InkPaletteHelper.parseHexRgbToArgbInt(colorKey) ?: when (colorKey) {
            "AMBER" -> Color.parseColor(if (isDarkSurface) "#B4ACEC" else "#6965DB")
            "SLATE" -> Color.parseColor(if (isDarkSurface) "#ADB5BD" else "#7E706C")
            "BLUE" -> Color.parseColor(if (isDarkSurface) "#74C0FC" else "#1971C2")
            "GREEN" -> Color.parseColor(if (isDarkSurface) "#69DB7C" else "#2F9E44")
            "CORAL" -> Color.parseColor(if (isDarkSurface) "#FF8787" else "#E03131")
            else -> Color.parseColor(if (isDarkSurface) "#E5E5EC" else "#121212")
        }
        return if (isHighlighter) {
            Color.argb(88, Color.red(base), Color.green(base), Color.blue(base))
        } else {
            base
        }
    }
}
