package g.p.cbb.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import g.p.cbb.data.entity.Customer
import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {
    fun generateCustomerLedger(context: Context, customer: Customer, transactions: List<Transaction>) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        var y = 40f

        // Title
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Customer Ledger Report", 180f, y, paint)
        y += 40f

        // Customer Info
        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText("Name: ${customer.name}", 40f, y, paint)
        y += 20f
        canvas.drawText("Phone: ${customer.phone}", 40f, y, paint)
        y += 20f
        canvas.drawText("Current Balance: ₹${"%.2f".format(customer.totalBalance)}", 40f, y, paint)
        y += 40f

        // Table Header
        paint.isFakeBoldText = true
        canvas.drawText("Date", 40f, y, paint)
        canvas.drawText("Note", 180f, y, paint)
        canvas.drawText("Type", 380f, y, paint)
        canvas.drawText("Amount", 480f, y, paint)
        y += 20f
        canvas.drawLine(40f, y, 555f, y, paint)
        y += 20f

        // Table Content
        paint.isFakeBoldText = false
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        transactions.forEach { transaction ->
            if (y > 800) {
                pdfDocument.finishPage(page)
                // In a real app, we'd start a new page here. For simplicity, we stop.
                return@forEach 
            }
            canvas.drawText(dateFormat.format(Date(transaction.timestamp)), 40f, y, paint)
            canvas.drawText(if (transaction.note.length > 20) transaction.note.take(17) + "..." else transaction.note, 180f, y, paint)
            canvas.drawText(transaction.type.name, 380f, y, paint)
            canvas.drawText("₹${"%.2f".format(transaction.amount)}", 480f, y, paint)
            y += 20f
        }

        pdfDocument.finishPage(page)

        val fileName = "Statement_${customer.name}_${System.currentTimeMillis()}.pdf"
        val statementFolder = StorageManager.getStatementFolder(context)
        val file = File(statementFolder, fileName)

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            // Scan file so it shows up in file manager immediately
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            Toast.makeText(context, "Statement Saved: Documents/udaari/statements", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
        }

        pdfDocument.close()
    }
}
