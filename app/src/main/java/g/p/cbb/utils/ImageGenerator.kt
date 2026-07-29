package g.p.cbb.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.core.content.FileProvider
import g.p.cbb.data.entity.BillItem
import g.p.cbb.data.entity.Customer
import g.p.cbb.data.entity.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ImageGenerator {
    fun shareBillImage(
        context: Context,
        customer: Customer,
        bill: Transaction,
        items: List<BillItem>,
        payments: List<Transaction>,
        attachmentPath: String? = null
    ) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val totalPaid = payments.sumOf { it.amount }
        
        val width = 600
        val baseHeight = 900
        val itemsHeight = (items.size * 40)
        val paymentsHeight = (payments.size * 40)
        val totalHeight = baseHeight + itemsHeight + paymentsHeight
        
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Background and Border
        canvas.drawColor(Color.WHITE)
        val borderPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(10f, 10f, width - 10f, totalHeight - 10f, borderPaint)

        var y = 80f
        paint.color = Color.BLACK
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText("CUSTOMER BILL", 200f, y, paint)
        
        y += 60f
        paint.textSize = 24f
        paint.isFakeBoldText = false
        canvas.drawText("Customer: ${customer.name}", 40f, y, paint)
        y += 40f
        canvas.drawText("Phone: ${customer.phone}", 40f, y, paint)
        y += 40f
        canvas.drawText("Bill Date: ${dateFormat.format(Date(bill.timestamp))}", 40f, y, paint)
        y += 60f
        
        if (items.isNotEmpty()) {
            paint.isFakeBoldText = true
            canvas.drawText("Items", 40f, y, paint)
            canvas.drawText("Price", 480f, y, paint)
            y += 20f
            canvas.drawLine(40f, y, 560f, y, paint)
            y += 40f
            
            paint.isFakeBoldText = false
            items.forEach { item ->
                canvas.drawText(item.productName, 40f, y, paint)
                canvas.drawText("₹${"%.2f".format(item.price)}", 480f, y, paint)
                y += 40f
            }
            y += 20f
            canvas.drawLine(40f, y, 560f, y, paint)
            y += 40f
        } else {
            // Prominent Lumpsum text
            paint.color = Color.parseColor("#3F51B5")
            paint.isFakeBoldText = true
            paint.textSize = 28f
            canvas.drawText("LUMPSUM TRANSACTION", 40f, y, paint)
            y += 40f
            
            paint.color = Color.BLACK
            paint.textSize = 32f
            canvas.drawText("Amount: ₹${"%.2f".format(bill.amount)}", 40f, y, paint)
            y += 45f
            
            if (bill.note.isNotEmpty()) {
                paint.textSize = 24f
                paint.isFakeBoldText = false
                canvas.drawText("Note: ${bill.note}", 40f, y, paint)
                y += 40f
            }
            
            if (attachmentPath != null) {
                paint.color = Color.GRAY
                paint.textSize = 20f
                canvas.drawText("(Photo Attachment Included)", 40f, y, paint)
                y += 40f
            }
            y += 10f
            paint.color = Color.BLACK
        }
        
        paint.isFakeBoldText = true
        canvas.drawText("Total Bill Amount:", 40f, y, paint)
        canvas.drawText("₹${"%.2f".format(bill.amount)}", 480f, y, paint)
        y += 60f

        if (payments.isNotEmpty()) {
            paint.color = Color.parseColor("#4CAF50")
            paint.isFakeBoldText = true
            canvas.drawText("Received Payments", 40f, y, paint)
            y += 20f
            canvas.drawLine(40f, y, 560f, y, paint)
            y += 40f
            
            paint.isFakeBoldText = false
            paint.textSize = 20f
            payments.forEach { payment ->
                canvas.drawText(dateFormat.format(Date(payment.timestamp)), 40f, y, paint)
                canvas.drawText("₹${"%.2f".format(payment.amount)}", 480f, y, paint)
                y += 40f
            }
            y += 20f
        }
        
        paint.color = Color.BLACK
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("Total Received:", 40f, y, paint)
        canvas.drawText("₹${"%.2f".format(totalPaid)}", 480f, y, paint)
        y += 40f
        
        paint.color = Color.parseColor("#F44336")
        canvas.drawText("Remaining Balance:", 40f, y, paint)
        canvas.drawText("₹${"%.2f".format(bill.amount - totalPaid)}", 480f, y, paint)
        
        // Save Summary Image
        val cacheFile = File(context.cacheDir, "bill_summary.png")
        FileOutputStream(cacheFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Save permanent copy in udaari
        val invoiceFolder = StorageManager.getInvoiceFolder(context)
        val permFile = File(invoiceFolder, "Invoice_${customer.name}_${System.currentTimeMillis()}.png")
        FileOutputStream(permFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(permFile.absolutePath), null, null)

        try {
            val summaryUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", cacheFile)
            
            val uris = arrayListOf<Uri>(summaryUri)
            attachmentPath?.let { path ->
                val attachFile = File(path)
                if (attachFile.exists()) {
                    try {
                        uris.add(FileProvider.getUriForFile(context, "${context.packageName}.provider", attachFile))
                    } catch (e: Exception) {
                        Log.e("ImageGenerator", "Error getting URI for attachment: ${e.message}")
                    }
                }
            }

            val toast = Toast.makeText(context, "Bill Saved to gallery", Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.TOP, 0, 100)
            toast.show()

            val intent = if (uris.size > 1) {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, summaryUri)
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Share Bill"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
