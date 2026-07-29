package g.p.cbb.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {
    
    /**
     * Compresses and saves an image from a URI to the attachments folder.
     * Targets max 1200px width/height and 80% JPEG quality.
     */
    fun saveCompressedAttachment(context: Context, imageUri: Uri): String? {
        return try {
            val attachmentFolder = StorageManager.getAttachmentFolder(context)
            val fileName = "attachment_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}.jpg"
            val destFile = File(attachmentFolder, fileName)

            context.contentResolver.openInputStream(imageUri).use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(context.contentResolver.openInputStream(imageUri), null, options)
                
                // Calculate scale factor
                var scale = 1
                val maxDim = 1200
                if (options.outHeight > maxDim || options.outWidth > maxDim) {
                    scale = Math.pow(2.0, Math.ceil(Math.log(maxDim.toDouble() / Math.max(options.outHeight, options.outWidth)) / Math.log(0.5)).toInt().toDouble()).toInt()
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
                val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(imageUri), null, decodeOptions)
                
                bitmap?.let {
                    FileOutputStream(destFile).use { out ->
                        it.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    destFile.absolutePath
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
