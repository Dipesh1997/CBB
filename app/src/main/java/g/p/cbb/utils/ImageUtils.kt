package g.p.cbb.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {
    
    /**
     * Compresses and saves an image from a URI to app's permanent attachments folder.
     * Targets max 1200px width/height and 80% JPEG quality.
     */
    fun saveCompressedAttachment(context: Context, imageUri: Uri): String? {
        return try {
            val attachmentFolder = StorageManager.getAttachmentFolder(context)
            val fileName = "attachment_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}.jpg"
            val destFile = File(attachmentFolder, fileName)

            var bitmap: Bitmap? = null

            try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    
                    var scale = 1
                    val maxDim = 1200
                    if (options.outHeight > maxDim || options.outWidth > maxDim) {
                        val maxVal = Math.max(options.outHeight, options.outWidth)
                        scale = Math.round(maxVal.toFloat() / maxDim.toFloat()).coerceAtLeast(1)
                    }

                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
                    context.contentResolver.openInputStream(imageUri)?.use { stream2 ->
                        bitmap = BitmapFactory.decodeStream(stream2, null, decodeOptions)
                    }
                }
            } catch (e: Exception) {
                Log.w("ImageUtils", "Bitmap scaling failed, falling back to raw copy: ${e.message}")
            }

            if (bitmap != null) {
                FileOutputStream(destFile).use { out ->
                    bitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
            } else {
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                Log.i("ImageUtils", "Saved permanent attachment: ${destFile.absolutePath}")
                destFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error saving attachment: ${e.message}", e)
            null
        }
    }

    fun ensurePermanentLocalPath(context: Context, pathOrUri: String?): String? {
        if (pathOrUri.isNullOrBlank()) return null
        val trimmed = pathOrUri.trim()

        val cleanPath = if (trimmed.startsWith("file://")) trimmed.substring(7) else trimmed
        val localFile = File(cleanPath)
        if (localFile.exists() && localFile.length() > 0) {
            return localFile.absolutePath
        }

        if (trimmed.startsWith("content://")) {
            val savedPath = saveCompressedAttachment(context, Uri.parse(trimmed))
            if (savedPath != null) return savedPath
        }

        return trimmed
    }
}
