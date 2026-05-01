package com.example.irondiary.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.provider.MediaStore
import com.example.irondiary.data.DailyLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

                // Draw Title
                canvas.drawText("IronDiary Calendar Data", margin, currentY + 18f, titlePaint)
                currentY += 40f

                // Define Column Widths
                val colDate = margin
                val colGym = margin + 80f
                val colWeight = margin + 150f
                val colNotes = margin + 220f
                val maxNotesWidth = pageWidth - margin - colNotes

                // Draw Table Header
                canvas.drawText("Date", colDate, currentY, headerPaint)
                canvas.drawText("Gym", colGym, currentY, headerPaint)
                canvas.drawText("Weight", colWeight, currentY, headerPaint)
                canvas.drawText("Notes", colNotes, currentY, headerPaint)
                
                currentY += 10f
                canvas.drawLine(margin, currentY, pageWidth - margin, currentY, paint)
                currentY += 20f

                for (log in logs) {
                    // Check if we need a new page before drawing the next row
                    if (currentY > pageHeight - margin - 50f) {
                        document.finishPage(page)
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create()
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = margin
                        
                        // Redraw header on new page
                        canvas.drawText("Date", colDate, currentY, headerPaint)
                        canvas.drawText("Gym", colGym, currentY, headerPaint)
                        canvas.drawText("Weight", colWeight, currentY, headerPaint)
                        canvas.drawText("Notes", colNotes, currentY, headerPaint)
                        currentY += 10f
                        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, paint)
                        currentY += 20f
                    }

                    // Prepare cell contents
                    val dateText = log.date
                    val gymText = if (log.attendedGym) "Yes" else "No"
                    val weightText = log.weight?.let { "${it}kg" } ?: "--"
                    val notesText = log.notes ?: ""

                    // Draw Date, Gym, Weight (assume they fit on one line)
                    canvas.drawText(dateText, colDate, currentY, paint)
                    canvas.drawText(gymText, colGym, currentY, paint)
                    canvas.drawText(weightText, colWeight, currentY, paint)

                    // Draw Notes (handle wrapping)
                    val words = notesText.split(" ")
                    var line = ""
                    var notesY = currentY
                    for (word in words) {
                        val testLine = if (line.isEmpty()) word else "$line $word"
                        val testWidth = paint.measureText(testLine)
                        if (testWidth > maxNotesWidth && line.isNotEmpty()) {
                            canvas.drawText(line, colNotes, notesY, paint)
                            line = word
                            notesY += 15f // line height
                            
                            // Check page bounds during wrap
                            if (notesY > pageHeight - margin) {
                                document.finishPage(page)
                                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create()
                                page = document.startPage(pageInfo)
                                canvas = page.canvas
                                notesY = margin
                            }
                        } else {
                            line = testLine
                        }
                    }
                    if (line.isNotEmpty()) {
                        canvas.drawText(line, colNotes, notesY, paint)
                    }

                    // Update currentY for next row
                    currentY = (if (notesY > currentY) notesY else currentY) + 20f
                    canvas.drawLine(margin, currentY - 10f, pageWidth - margin, currentY - 10f, paint)
                }

                document.finishPage(page)

                // Save to MediaStore
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "IronDiary_Export_$timeStamp.pdf"

                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/IronDiary")
                    }
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
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
