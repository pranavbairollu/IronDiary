package com.example.irondiary.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.irondiary.data.DailyLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportUtility {

    suspend fun exportLogsToPdf(context: Context, logs: List<DailyLog>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val document = PdfDocument()

                // A4 page size in points (1/72 inch) -> 595 x 842
                val pageWidth = 595
                val pageHeight = 842
                
                var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                var page = document.startPage(pageInfo)
                var canvas = page.canvas

                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 12f
                    isAntiAlias = true
                }
                
                val textPaint = TextPaint(paint)
                
                val headerPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 14f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val titlePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }

                val margin = 40f
                var currentY = margin

                // Define Column Widths
                val colDate = margin
                val colGym = margin + 80f
                val colWeight = margin + 150f
                val colNotes = margin + 220f
                val maxNotesWidth = pageWidth - margin - colNotes

                fun drawHeader(canvas: Canvas, y: Float) {
                    canvas.drawText("Date", colDate, y, headerPaint)
                    canvas.drawText("Gym", colGym, y, headerPaint)
                    canvas.drawText("Weight", colWeight, y, headerPaint)
                    canvas.drawText("Notes", colNotes, y, headerPaint)
                }

                // Draw Title
                canvas.drawText("IronDiary Calendar Data", margin, currentY + 18f, titlePaint)
                currentY += 40f

                // Draw Table Header
                drawHeader(canvas, currentY)
                currentY += 10f
                canvas.drawLine(margin, currentY, pageWidth - margin, currentY, paint)
                currentY += 20f

                for (log in logs) {
                    val dateText = log.date
                    val gymText = if (log.attendedGym) "Yes" else "No"
                    val weightText = log.weight?.let { "${it}kg" } ?: "--"
                    val notesText = log.notes ?: ""

                    // Create StaticLayout for notes
                    val staticLayout = StaticLayout.Builder.obtain(notesText, 0, notesText.length, textPaint, maxNotesWidth.toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1f)
                        .setIncludePad(false)
                        .build()

                    val rowHeight = Math.max(20f, staticLayout.height.toFloat() + 10f)

                    // Check if we need a new page before drawing this row
                    if (currentY + rowHeight > pageHeight - margin) {
                        document.finishPage(page)
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create()
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = margin
                        
                        // Redraw header on new page
                        drawHeader(canvas, currentY)
                        currentY += 10f
                        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, paint)
                        currentY += 20f
                    }

                    // Draw Date, Gym, Weight
                    canvas.drawText(dateText, colDate, currentY, paint)
                    canvas.drawText(gymText, colGym, currentY, paint)
                    canvas.drawText(weightText, colWeight, currentY, paint)

                    // Draw Notes
                    if (notesText.isNotEmpty()) {
                        canvas.save()
                        // StaticLayout draws from its top-left, but drawText uses baseline. 
                        // So we adjust Y slightly so it aligns with the single-line texts.
                        canvas.translate(colNotes, currentY - 10f)
                        staticLayout.draw(canvas)
                        canvas.restore()
                    }

                    // Update currentY for next row
                    currentY += rowHeight
                    canvas.drawLine(margin, currentY - 10f, pageWidth - margin, currentY - 10f, paint)
                }

                document.finishPage(page)

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "IronDiary_Export_$timeStamp.pdf"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/IronDiary")
                    }

                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        ?: return@withContext Result.failure(Exception("Failed to create MediaStore record"))

                    var os: OutputStream? = null
                    try {
                        os = resolver.openOutputStream(uri)
                        if (os != null) {
                            document.writeTo(os)
                        } else {
                            return@withContext Result.failure(Exception("Failed to open output stream"))
                        }
                    } finally {
                        os?.close()
                        document.close()
                    }
                    Result.success("Saved to Downloads/IronDiary/$fileName")
                } else {
                    // Fallback for API 26-28
                    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    if (downloadsDir != null) {
                        if (!downloadsDir.exists()) downloadsDir.mkdirs()
                        val file = File(downloadsDir, fileName)
                        val os = FileOutputStream(file)
                        try {
                            document.writeTo(os)
                        } finally {
                            os.close()
                            document.close()
                        }
                        Result.success("Saved to ${file.absolutePath}")
                    } else {
                        document.close()
                        Result.failure(Exception("Could not access external storage"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
