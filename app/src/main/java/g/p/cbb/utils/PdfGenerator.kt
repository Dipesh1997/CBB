package g.p.cbb.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import g.p.cbb.data.entity.Customer
import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import g.p.cbb.data.model.TransactionWithDetails
import java.io.File
import java.io.FileOutputStream
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
        detailLevel: PdfDetailLevel = PdfDetailLevel.SUMMARY
    ) {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        var pageNumber = 1
        var currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = currentPage.canvas
        var y = MARGIN

        // Title
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Customer Ledger Report", 180f, y, paint)
        y += 40f

        // Customer Info
        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText("Name: ${customer.name}", MARGIN, y, paint)
        y += 20f
        canvas.drawText("Phone: ${customer.phone}", MARGIN, y, paint)
        y += 20f
        canvas.drawText("Current Balance: ₹${"%.2f".format(customer.totalBalance)}", MARGIN, y, paint)
        y += 40f

        if (detailLevel == PdfDetailLevel.SUMMARY) {
            drawSummaryTable(pdfDocument, currentPage, canvas, paint, transactions, y)
        } else {
            drawDetailedList(pdfDocument, currentPage, canvas, paint, transactions, y)
        }

        val fileName = "Statement_${customer.name}_${System.currentTimeMillis()}.pdf"
        val statementFolder = StorageManager.getStatementFolder(context)
        val file = File(statementFolder, fileName)

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            Toast.makeText(context, "Statement Saved: Documents/udaari/statements", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
        }

        pdfDocument.close()
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
            if (y > PAGE_HEIGHT - MARGIN) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = currentPage.canvas
                y = MARGIN
                
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
            val billItems = item.billItems
            
            // Check if we need a new page for the transaction header
            if (y > PAGE_HEIGHT - 150f) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = currentPage.canvas
                y = MARGIN
            }

            // Transaction Header
            paint.isFakeBoldText = true
            paint.textSize = 14f
            paint.color = Color.DKGRAY
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 30f, paint)
            paint.color = Color.WHITE
            canvas.drawText("${transaction.type} - ${dateFormat.format(Date(transaction.timestamp))}", MARGIN + 10f, y + 20f, paint)
            paint.color = Color.BLACK
            y += 50f

            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Amount: ₹${"%.2f".format(transaction.amount)}", MARGIN, y, paint)
            y += 20f
            if (transaction.note.isNotEmpty()) {
                canvas.drawText("Note: ${transaction.note}", MARGIN, y, paint)
                y += 20f
            }

            // Draw Bill Items
            if (billItems.isNotEmpty()) {
                paint.isFakeBoldText = true
                canvas.drawText("Items:", MARGIN, y, paint)
                y += 20f
                paint.isFakeBoldText = false
                billItems.forEach { billItem ->
                    if (y > PAGE_HEIGHT - MARGIN) {
                        pdfDocument.finishPage(currentPage)
                        pageNumber++
                        currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                        canvas = currentPage.canvas
                        y = MARGIN
                    }
                    canvas.drawText("• ${billItem.productName}", MARGIN + 20f, y, paint)
                    canvas.drawText("₹${"%.2f".format(billItem.price)}", 480f, y, paint)
                    y += 20f
                }
            }

            // Draw Image Attachment
            transaction.attachmentPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    if (y > PAGE_HEIGHT - 250f) {
                        pdfDocument.finishPage(currentPage)
                        pageNumber++
                        currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                        canvas = currentPage.canvas
                        y = MARGIN
                    }
                    
                    try {
                        val bitmap = BitmapFactory.decodeFile(path)
                        if (bitmap != null) {
                            val scaledBitmap = scaleBitmap(bitmap, PAGE_WIDTH - (MARGIN * 2).toInt(), 200)
                            canvas.drawBitmap(scaledBitmap, MARGIN, y, paint)
                            y += scaledBitmap.height + 20f
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            y += 30f // Spacing between transactions
            canvas.drawLine(MARGIN, y - 15f, PAGE_WIDTH - MARGIN, y - 15f, paint)
            y += 10f
        }
        pdfDocument.finishPage(currentPage)
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
