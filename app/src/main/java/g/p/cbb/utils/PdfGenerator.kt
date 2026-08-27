package g.p.cbb.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import g.p.cbb.data.entity.Customer
import g.p.cbb.data.model.TransactionWithDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

enum class PdfDetailLevel {
    SUMMARY,
    DETAILED
}

object PdfGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun generateCustomerLedger(
        context: Context,
        customer: Customer,
        transactions: List<TransactionWithDetails>,
        detailLevel: PdfDetailLevel = PdfDetailLevel.DETAILED,
        startDate: Long? = null,
        endDate: Long? = null,
        sinceLastZeroBalance: Boolean = false
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val initialList = if (sinceLastZeroBalance) {
                    LedgerUtils.getTransactionsSinceLastZeroBalance(transactions)
                } else {
                    transactions
                }

                val filteredTransactions = initialList.filter { item ->
                    val matchesCustomer = item.transaction.customerId == customer.id
                    val ts = item.transaction.timestamp
                    val afterStart = startDate == null || ts >= startDate
                    val beforeEnd = endDate == null || ts <= endDate
                    matchesCustomer && afterStart && beforeEnd
                }.sortedByDescending { it.transaction.timestamp }

                val pdfDocument = PdfDocument()
                val paint = Paint()
                var pageNumber = 1
                var currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                var canvas = currentPage.canvas
                var y = MARGIN + 20f

                // Centered Title
                paint.textSize = 20f
                paint.isFakeBoldText = true
                paint.color = Color.BLACK
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("Customer Ledger Report", PAGE_WIDTH / 2f, y, paint)
                y += 40f

                // Customer Info
                paint.textAlign = Paint.Align.LEFT
                paint.textSize = 13f
                paint.isFakeBoldText = false
                canvas.drawText("Name: ${customer.name}", MARGIN, y, paint)
                y += 20f
                canvas.drawText("Phone: ${customer.phone}", MARGIN, y, paint)
                y += 20f
                canvas.drawText("Current Balance: ₹${"%.2f".format(customer.totalBalance)}", MARGIN, y, paint)
                y += 20f

                if (sinceLastZeroBalance) {
                    paint.textSize = 12f
                    paint.color = Color.parseColor("#1B5E20")
                    paint.isFakeBoldText = true
                    canvas.drawText("Filter: Statement since last cleared balance (₹0.00)", MARGIN, y, paint)
                    y += 20f
                    paint.color = Color.BLACK
                    paint.isFakeBoldText = false
                } else if (startDate != null && endDate != null) {
                    val rangeFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    canvas.drawText("Date Range: ${rangeFormat.format(Date(startDate))} - ${rangeFormat.format(Date(endDate))}", MARGIN, y, paint)
                    y += 20f
                }
                y += 20f

                if (detailLevel == PdfDetailLevel.SUMMARY) {
                    drawSummaryTable(pdfDocument, currentPage, canvas, paint, filteredTransactions, y)
                } else {
                    drawDetailedList(context, pdfDocument, currentPage, canvas, paint, filteredTransactions, y)
                }

                val fileName = "Statement_${customer.name.replace("\\s+".toRegex(), "_")}_${System.currentTimeMillis()}.pdf"
                val statementFolder = StorageManager.getStatementFolder(context)
                val file = File(statementFolder, fileName)

                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/udaari/statements")
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outStream ->
                                file.inputStream().use { inputStream ->
                                    inputStream.copyTo(outStream)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Statement Saved to App Storage & Downloads", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Error generating PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun drawSummaryTable(
        pdfDocument: PdfDocument,
        initialPage: PdfDocument.Page,
        initialCanvas: Canvas,
        paint: Paint,
        transactions: List<TransactionWithDetails>,
        startY: Float
    ) {
        var currentPage = initialPage
        var canvas = initialCanvas
        var y = startY
        var pageNumber = 1

        // Table Header
        paint.isFakeBoldText = true
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Date", MARGIN, y, paint)
        canvas.drawText("Note", 140f, y, paint)
        canvas.drawText("Type", 380f, y, paint)
        canvas.drawText("Amount", 480f, y, paint)
        y += 15f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)
        y += 20f

        // Table Content
        paint.isFakeBoldText = false
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        transactions.forEach { item ->
            val transaction = item.transaction
            if (y > PAGE_HEIGHT - MARGIN - 30f) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = currentPage.canvas
                y = MARGIN + 20f

                // Redraw Header on new page
                paint.isFakeBoldText = true
                canvas.drawText("Date", MARGIN, y, paint)
                canvas.drawText("Note", 140f, y, paint)
                canvas.drawText("Type", 380f, y, paint)
                canvas.drawText("Amount", 480f, y, paint)
                y += 15f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)
                y += 20f
                paint.isFakeBoldText = false
            }

            canvas.drawText(dateFormat.format(Date(transaction.timestamp)), MARGIN, y, paint)
            val note = if (transaction.note.length > 25) transaction.note.take(22) + "..." else transaction.note
            canvas.drawText(note, 140f, y, paint)
            canvas.drawText(transaction.type.name, 380f, y, paint)
            canvas.drawText("₹${"%.2f".format(transaction.amount)}", 480f, y, paint)
            y += 25f
        }
        pdfDocument.finishPage(currentPage)
    }

    private fun drawDetailedList(
        context: Context,
        pdfDocument: PdfDocument,
        initialPage: PdfDocument.Page,
        initialCanvas: Canvas,
        paint: Paint,
        transactions: List<TransactionWithDetails>,
        startY: Float
    ) {
        var currentPage = initialPage
        var canvas = initialCanvas
        var y = startY
        var pageNumber = 1
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        transactions.forEach { item ->
            val transaction = item.transaction

            // Check if we need a new page for the transaction block
            if (y > PAGE_HEIGHT - 120f) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = currentPage.canvas
                y = MARGIN + 20f
            }

            // Dark Transaction Header Rect
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 28f, paint)

            // Header Text in White
            paint.color = Color.WHITE
            paint.isFakeBoldText = true
            paint.textSize = 13f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${transaction.type.name} - ${dateFormat.format(Date(transaction.timestamp))}", MARGIN + 10f, y + 19f, paint)
            paint.color = Color.BLACK
            y += 45f

            // Transaction Details
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Amount: ₹${"%.2f".format(transaction.amount)}", MARGIN, y, paint)
            y += 20f

            if (transaction.note.isNotBlank()) {
                canvas.drawText("Note: ${transaction.note}", MARGIN, y, paint)
                y += 20f
            }

            // Draw Image Attachment with Full Resolution (No downscaling of bitmap pixels)
            val bitmap = loadTransactionBitmap(context, transaction.attachmentPath, transaction.driveFileId)
            if (bitmap != null) {
                val maxDrawWidth = PAGE_WIDTH - MARGIN * 2 // 515f
                val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                var drawWidth = maxDrawWidth
                var drawHeight = drawWidth / aspectRatio

                // Limit display height per bill entry to 420pt so it fits nicely on page
                if (drawHeight > 420f) {
                    drawHeight = 420f
                    drawWidth = drawHeight * aspectRatio
                }

                val drawLeft = MARGIN + (maxDrawWidth - drawWidth) / 2f

                if (y + drawHeight > PAGE_HEIGHT - MARGIN - 20f) {
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                    canvas = currentPage.canvas
                    y = MARGIN + 20f
                }

                val destRect = android.graphics.RectF(drawLeft, y, drawLeft + drawWidth, y + drawHeight)
                // Draw original uncompressed bitmap directly to canvas RectF so PDF zooming retains crispness
                canvas.drawBitmap(bitmap, null, destRect, paint)
                y += drawHeight + 20f
            }

            // Divider Line
            y += 10f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = Color.LTGRAY
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            y += 25f
        }
        pdfDocument.finishPage(currentPage)
    }

    private fun loadTransactionBitmap(context: Context, path: String?, driveFileId: String?): Bitmap? {
        if (!path.isNullOrBlank()) {
            val bitmap = loadBitmapFromPath(context, path)
            if (bitmap != null) return bitmap
        }

        if (!driveFileId.isNullOrBlank()) {
            val driveUrl1 = "https://lh3.googleusercontent.com/d/${driveFileId.trim()}=w2000"
            val bitmap1 = loadBitmapFromPath(context, driveUrl1)
            if (bitmap1 != null) return bitmap1

            val driveUrl2 = "https://drive.google.com/thumbnail?id=${driveFileId.trim()}&sz=w2000"
            return loadBitmapFromPath(context, driveUrl2)
        }

        return null
    }

    private fun loadBitmapFromPath(context: Context, path: String): Bitmap? {
        return ImageResolver.loadBitmap(context, path, null)
    }

    private fun scaleBitmap(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        var width = source.width
        var height = source.height
        val aspectRatio: Float = width.toFloat() / height.toFloat()

        if (width > maxWidth) {
            width = maxWidth
            height = (width / aspectRatio).toInt()
        }

        if (height > maxHeight) {
            height = maxHeight
            width = (height * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}

