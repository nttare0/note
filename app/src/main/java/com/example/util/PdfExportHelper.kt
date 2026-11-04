package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.ChecklistItem
import com.example.data.InkStroke
import com.example.data.NoteEntity
import com.example.data.NoteImageAttachment
import com.example.data.PaperTemplate
import java.io.File
import java.io.FileOutputStream

object PdfExportHelper {

    fun exportAndShareNotePdf(
        context: Context,
        note: NoteEntity,
        checklist: List<ChecklistItem>,
        strokes: List<InkStroke>,
        images: List<NoteImageAttachment> = emptyList()
    ): Boolean {
        return try {
            val document = PdfDocument()
            val pageWidth = 595 // Standard A4 postscript points
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Background
            val bgPaint = Paint().apply {
                color = Color.parseColor("#F8F9FA")
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

            // Draw Paper Template
            drawTemplateOnPdf(canvas, pageWidth.toFloat(), pageHeight.toFloat(), note.template)

            // Title
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#121212")
                textSize = 22f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val displayTitle = note.title.ifBlank { "Untitled Note" }
            canvas.drawText(displayTitle, 48f, 64f, titlePaint)

            var cursorY = 98f

            // Body text wrapped manually
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E1E24")
                textSize = 13f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            if (note.body.isNotBlank()) {
                val maxLineWidth = pageWidth - 96f
                note.body.split("\n").forEach { paragraph ->
                    val words = paragraph.split(" ")
                    var line = ""
                    for (word in words) {
                        val candidate = if (line.isEmpty()) word else "$line $word"
                        if (bodyPaint.measureText(candidate) <= maxLineWidth) {
                            line = candidate
                        } else {
                            canvas.drawText(line, 48f, cursorY, bodyPaint)
                            cursorY += 20f
                            line = word
                        }
                    }
                    if (line.isNotEmpty()) {
                        canvas.drawText(line, 48f, cursorY, bodyPaint)
                        cursorY += 22f
                    }
                }
                cursorY += 8f
            }

            // Attached Images (up to 2 thumbnails neatly arranged so A4 page remains balanced)
            if (images.isNotEmpty()) {
                var imgX = 48f
                val thumbMaxW = 152f
                val thumbMaxH = 112f
                images.take(3).forEach { attachment ->
                    val file = File(attachment.localPath)
                    if (file.exists()) {
                        val bmp = BitmapFactory.decodeFile(file.absolutePath)
                        if (bmp != null) {
                            val ratio = (bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f))
                                .coerceIn(0.6f, 2.0f)
                            val drawW = thumbMaxW
                            val drawH = (drawW / ratio).coerceAtMost(thumbMaxH)
                            val dst = RectF(imgX, cursorY, imgX + drawW, cursorY + drawH)
                            canvas.drawBitmap(bmp, null, dst, null)
                            bmp.recycle()
                            imgX += drawW + 12f
                        }
                    }
                }
                cursorY += thumbMaxH + 18f
            }

            // Checklist items
            if (checklist.isNotEmpty()) {
                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#71717A")
                    style = Paint.Style.STROKE
                    strokeWidth = 1.4f
                }
                val checkedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#6965DB")
                    style = Paint.Style.FILL
                }
                checklist.forEach { item ->
                    if (item.isChecked) {
                        canvas.drawRoundRect(48f, cursorY - 11f, 60f, cursorY + 1f, 3f, 3f, checkedPaint)
                    } else {
                        canvas.drawRoundRect(48f, cursorY - 11f, 60f, cursorY + 1f, 3f, 3f, boxPaint)
                    }
                    canvas.drawText(item.text, 68f, cursorY, bodyPaint)
                    cursorY += 22f
                }
                cursorY += 12f
            }

            // Vector Ink Strokes (preserving stylus pressure variable line thickness)
            if (strokes.isNotEmpty()) {
                val offsetY = cursorY.coerceAtMost(480f)
                strokes.forEach { stroke ->
                    if (stroke.points.isNotEmpty()) {
                        val inkColor = resolveAndroidColor(stroke.colorKey, stroke.isHighlighter)
                        val useVariablePressure = StrokePressureEngine.hasVariablePressure(
                            points = stroke.points,
                            isStylusStroke = stroke.isStylus
                        )
                        if (useVariablePressure) {
                            val spine = StrokePressureEngine.buildVariableWidthSpine(
                                points = stroke.points,
                                baseWidthDp = stroke.widthDp * 1.4f,
                                isHighlighter = stroke.isHighlighter,
                                maxStepDp = 2.0f
                            )
                            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = inkColor
                                style = Paint.Style.FILL
                            }
                            if (spine.size == 1) {
                                canvas.drawCircle(
                                    48f + spine[0].xDp,
                                    offsetY + spine[0].yDp,
                                    spine[0].radiusDp,
                                    fillPaint
                                )
                            } else if (spine.size > 1) {
                                val ribbonPath = Path().apply {
                                    fillType = Path.FillType.WINDING
                                    var prevX = 48f + spine[0].xDp
                                    var prevY = offsetY + spine[0].yDp
                                    var prevR = spine[0].radiusDp
                                    addCircle(prevX, prevY, prevR, Path.Direction.CW)
                                    for (i in 1 until spine.size) {
                                        val curX = 48f + spine[i].xDp
                                        val curY = offsetY + spine[i].yDp
                                        val curR = spine[i].radiusDp
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
                        } else {
                            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = inkColor
                                style = Paint.Style.STROKE
                                strokeWidth = stroke.widthDp * 1.4f
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }
                            val path = Path()
                            val first = stroke.points.first()
                            path.moveTo(48f + first.x, offsetY + first.y)
                            for (i in 1 until stroke.points.size) {
                                val prev = stroke.points[i - 1]
                                val curr = stroke.points[i]
                                val midX = 48f + (prev.x + curr.x) / 2f
                                val midY = offsetY + (prev.y + curr.y) / 2f
                                path.quadTo(48f + prev.x, offsetY + prev.y, midX, midY)
                            }
                            canvas.drawPath(path, strokePaint)
                        }
                    }
                }
            }

            document.finishPage(page)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = displayTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "note" }
            val pdfFile = File(exportDir, "${safeName}_${note.id}.pdf")
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, context.getString(R.string.pdf_share_title)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun drawTemplateOnPdf(canvas: Canvas, width: Float, height: Float, templateName: String) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E4E4E7")
            strokeWidth = 0.8f
        }
        when (runCatching { PaperTemplate.valueOf(templateName) }.getOrDefault(PaperTemplate.BLANK)) {
            PaperTemplate.BLANK -> Unit
            PaperTemplate.RULED -> {
                var y = 96f
                while (y < height - 40f) {
                    canvas.drawLine(40f, y, width - 40f, y, linePaint)
                    y += 24f
                }
            }
            PaperTemplate.GRID -> {
                var x = 40f
                while (x < width - 40f) {
                    canvas.drawLine(x, 84f, x, height - 40f, linePaint)
                    x += 24f
                }
                var y = 84f
                while (y < height - 40f) {
                    canvas.drawLine(40f, y, width - 40f, y, linePaint)
                    y += 24f
                }
            }
            PaperTemplate.DOTTED -> {
                linePaint.style = Paint.Style.FILL
                var x = 48f
                while (x < width - 40f) {
                    var y = 92f
                    while (y < height - 40f) {
                        canvas.drawCircle(x, y, 1.1f, linePaint)
                        y += 22f
                    }
                    x += 22f
                }
            }
            PaperTemplate.CORNELL -> {
                var y = 96f
                while (y < height - 120f) {
                    canvas.drawLine(40f, y, width - 40f, y, linePaint)
                    y += 24f
                }
                val accentLine = Paint(linePaint).apply {
                    color = Color.parseColor("#D4D4D8")
                    strokeWidth = 1.2f
                }
                canvas.drawLine(175f, 84f, 175f, height - 120f, accentLine)
                canvas.drawLine(40f, height - 120f, width - 40f, height - 120f, accentLine)
            }
        }
    }

    private fun resolveAndroidColor(colorKey: String, isHighlighter: Boolean): Int {
        val base = InkPaletteHelper.parseHexRgbToArgbInt(colorKey) ?: when (colorKey) {
            "AMBER" -> Color.parseColor("#6965DB")
            "SLATE" -> Color.parseColor("#7E706C")
            "BLUE" -> Color.parseColor("#1971C2")
            "GREEN" -> Color.parseColor("#2F9E44")
            "CORAL" -> Color.parseColor("#E03131")
            else -> Color.parseColor("#121212")
        }
        return if (isHighlighter) {
            Color.argb(88, Color.red(base), Color.green(base), Color.blue(base))
        } else {
            base
        }
    }
}
