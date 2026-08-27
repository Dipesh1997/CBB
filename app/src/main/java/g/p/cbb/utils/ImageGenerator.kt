package g.p.cbb.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import g.p.cbb.data.entity.Customer
import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ImageGenerator {

    fun shareBillImage(
        context: Context,
        customer: Customer,
        bill: Transaction,
        payments: List<Transaction> = emptyList()
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val totalPaid = payments.sumOf { it.amount }
                val remainingBalance = (bill.amount - totalPaid).coerceAtLeast(0.0)

                // Load receipt image if available
                val receiptBitmap = loadReceiptBitmap(context, bill.attachmentPath, bill.driveFileId)
                var scaledReceipt: Bitmap? = null
                var receiptHeight = 0

                if (receiptBitmap != null) {
                    val maxPhotoWidth = 520f
                    val photoScale = maxPhotoWidth / receiptBitmap.width.toFloat()
                    val targetH = (receiptBitmap.height * photoScale).toInt().coerceIn(150, 550)
                    scaledReceipt = Bitmap.createScaledBitmap(receiptBitmap, maxPhotoWidth.toInt(), targetH, true)
                    receiptHeight = targetH + 50
                }

                val width = 600
                val headerHeight = 90
                val customerInfoHeight = 130
                val billDetailsHeight = 180
                val paymentsHeight = if (payments.isNotEmpty()) (payments.size * 35) + 80 else 0
                val footerHeight = 60
                val totalHeight = headerHeight + customerInfoHeight + billDetailsHeight + paymentsHeight + receiptHeight + footerHeight

                val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                // Background
                canvas.drawColor(Color.WHITE)

                // Outer Border
                val borderPaint = Paint().apply {
                    color = Color.parseColor("#E0E0E0")
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }
                canvas.drawRect(8f, 8f, width - 8f, totalHeight - 8f, borderPaint)

                var y = 0f

                // 1. Dark Header Bar
                paint.color = Color.parseColor("#1A237E") // Deep Indigo
                paint.style = Paint.Style.FILL
                canvas.drawRect(8f, 8f, width - 8f, y + headerHeight, paint)

                paint.color = Color.WHITE
                paint.textSize = 22f
                paint.isFakeBoldText = true
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("CUSTOMER BILL STATEMENT", width / 2f, y + 52f, paint)
                y += headerHeight

                // 2. Customer Info Section
                y += 25f
                paint.textAlign = Paint.Align.LEFT
                paint.color = Color.parseColor("#333333")
                paint.textSize = 17f
                paint.isFakeBoldText = true
                canvas.drawText("Customer: ${customer.name}", 35f, y, paint)
                y += 26f

                paint.textSize = 14f
                paint.isFakeBoldText = false
                paint.color = Color.parseColor("#666666")
                canvas.drawText("Phone: ${if (customer.phone.isNotBlank()) customer.phone else "N/A"}", 35f, y, paint)
                y += 24f

                canvas.drawText("Date & Time: ${dateFormat.format(Date(bill.timestamp))}", 35f, y, paint)
                y += 30f

                // Divider line
                paint.color = Color.parseColor("#E0E0E0")
                paint.strokeWidth = 2f
                canvas.drawLine(35f, y, width - 35f, y, paint)
                y += 25f

                // 3. Bill Transaction Box
                val isDebit = bill.type == TransactionType.DEBIT
                val badgeColor = if (isDebit) Color.parseColor("#B71C1C") else Color.parseColor("#1B5E20")
                val badgeBg = if (isDebit) Color.parseColor("#FFEBEE") else Color.parseColor("#E8F5E9")

                paint.color = badgeBg
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(35f, y, width - 35f, y + 130f, 12f, 12f, paint)

                paint.color = badgeColor
                paint.textSize = 15f
                paint.isFakeBoldText = true
                canvas.drawText(if (isDebit) "YOU GAVE (DEBIT BILL)" else "YOU GOT (CREDIT PAYMENT)", 55f, y + 35f, paint)

                paint.textSize = 28f
                paint.isFakeBoldText = true
                canvas.drawText("₹${"%.2f".format(bill.amount)}", 55f, y + 75f, paint)

                if (bill.note.isNotBlank()) {
                    paint.textSize = 14f
                    paint.isFakeBoldText = false
                    paint.color = Color.parseColor("#444444")
                    val truncatedNote = if (bill.note.length > 40) bill.note.take(37) + "..." else bill.note
                    canvas.drawText("Note: $truncatedNote", 55f, y + 105f, paint)
                }

                y += 150f

                // 4. Linked Part Payments Breakdown (if any)
                if (payments.isNotEmpty()) {
                    paint.color = Color.parseColor("#2E7D32")
                    paint.textSize = 15f
                    paint.isFakeBoldText = true
                    canvas.drawText("Part Payments Received (${payments.size})", 35f, y, paint)
                    y += 15f

                    paint.color = Color.parseColor("#C8E6C9")
                    canvas.drawRect(35f, y, width - 35f, y + 2f, paint)
                    y += 22f

                    paint.isFakeBoldText = false
                    paint.textSize = 13f
                    paint.color = Color.parseColor("#333333")

                    payments.forEach { p ->
                        canvas.drawText(dateFormat.format(Date(p.timestamp)), 35f, y, paint)
                        canvas.drawText("₹${"%.2f".format(p.amount)}", width - 150f, y, paint)
                        y += 30f
                    }

                    y += 10f
                    paint.isFakeBoldText = true
                    paint.textSize = 14f
                    canvas.drawText("Total Received: ₹${"%.2f".format(totalPaid)}", 35f, y, paint)
                    canvas.drawText("Remaining: ₹${"%.2f".format(remainingBalance)}", width - 200f, y, paint)
                    y += 30f

                    paint.color = Color.parseColor("#E0E0E0")
                    canvas.drawLine(35f, y, width - 35f, y, paint)
                    y += 25f
                }

                // 5. Embedded Receipt Photo
                if (scaledReceipt != null) {
                    paint.color = Color.parseColor("#333333")
                    paint.textSize = 14f
                    paint.isFakeBoldText = true
                    canvas.drawText("Attached Receipt Photo:", 35f, y, paint)
                    y += 20f

                    canvas.drawBitmap(scaledReceipt, 40f, y, paint)
                    y += scaledReceipt.height + 25f
                }

                // 6. Footer Branding
                paint.color = Color.parseColor("#757575")
                paint.textSize = 12f
                paint.isFakeBoldText = false
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("Generated via Udaari App • Customer Ledger Record", width / 2f, y + 20f, paint)

                // Save image to files
                val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val cleanName = customer.name.replace("\\s+".toRegex(), "_")
                val fileName = "Bill_${cleanName}_tx${bill.id}_${timestampStr}.png"

                // App Invoices folder
                val appFile = File(StorageManager.getInvoiceFolder(context), fileName)
                FileOutputStream(appFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }

                // Save to Gallery via MediaStore API on Android 10+ (API 29+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/udaari/bills")
                            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            values.clear()
                            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                        }
                    } catch (e: Exception) {
                        Log.w("ImageGenerator", "Could not save to MediaStore: ${e.message}")
                    }
                }

                // Scan app file for MediaScanner
                MediaScannerConnection.scanFile(context, arrayOf(appFile.absolutePath), null, null)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Bill Image saved to Downloads & Gallery", Toast.LENGTH_LONG).show()

                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", appFile)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Bill Image via"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error generating bill image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadReceiptBitmap(context: Context, path: String?, driveFileId: String?): Bitmap? {
        return ImageResolver.loadBitmap(context, path, driveFileId)
    }
}
