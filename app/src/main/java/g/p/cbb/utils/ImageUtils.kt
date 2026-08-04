package g.p.cbb.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {
    
    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 82

    /**
     * Compresses and saves an image from a URI to app's permanent attachments folder.
     * Preserves aspect ratio, corrects EXIF rotation, and applies quality JPEG compression.
     */
    fun saveCompressedAttachment(context: Context, imageUri: Uri): String? {
        return try {
            val attachmentFolder = StorageManager.getAttachmentFolder(context)
            val fileName = "attachment_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}.jpg"
            val destFile = File(attachmentFolder, fileName)

            var bitmap = decodeAndScaleBitmapFromStream(
                getStream = { context.contentResolver.openInputStream(imageUri) },
                maxDim = MAX_DIMENSION
            )

            if (bitmap != null) {
                bitmap = rotateBitmapIfNeeded(context, imageUri, bitmap)
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            } else {
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                Log.i("ImageUtils", "Saved compressed attachment: ${destFile.absolutePath} (${destFile.length() / 1024} KB)")
                destFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error saving compressed attachment: ${e.message}", e)
            null
        }
    }

    /**
     * Saves a customer profile photo.
     * If [compress] is true, resizes and compresses image (JPEG 82% quality).
     * If [compress] is false, saves original image file directly.
     */
    fun saveCustomerProfilePhoto(context: Context, imageUri: Uri, compress: Boolean = true): String? {
        return try {
            val profileFolder = StorageManager.getProfileFolder(context)
            val extension = if (compress) "jpg" else (context.contentResolver.getType(imageUri)?.substringAfter("/") ?: "jpg")
            val fileName = "profile_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}.$extension"
            val destFile = File(profileFolder, fileName)

            if (compress) {
                var bitmap = decodeAndScaleBitmapFromStream(
                    getStream = { context.contentResolver.openInputStream(imageUri) },
                    maxDim = MAX_DIMENSION
                )

                if (bitmap != null) {
                    bitmap = rotateBitmapIfNeeded(context, imageUri, bitmap)
                    FileOutputStream(destFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    }
                } else {
                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } else {
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                Log.i("ImageUtils", "Saved customer profile photo (compressed=$compress): ${destFile.absolutePath} (${destFile.length() / 1024} KB)")
                destFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error saving customer profile photo: ${e.message}", e)
            null
        }
    }

    /**
     * Ensures an attachment file is compressed before uploading to Google Drive or cloud sync.
     * Retains original file if already small (<250KB) and scaled.
     */
    fun getCompressedFileForUpload(context: Context, originalFile: File): File {
        if (!originalFile.exists() || originalFile.length() == 0L) return originalFile
        
        if (originalFile.length() <= 250 * 1024) {
            return originalFile
        }

        return try {
            val compressedFolder = StorageManager.getAttachmentFolder(context)
            val fileName = "upload_${System.currentTimeMillis()}_${originalFile.name}"
            val compressedFile = File(compressedFolder, fileName)

            var bitmap = decodeAndScaleBitmapFromStream(
                getStream = { originalFile.inputStream() },
                maxDim = MAX_DIMENSION
            )

            if (bitmap != null) {
                bitmap = rotateBitmapIfNeeded(originalFile.absolutePath, bitmap)
                FileOutputStream(compressedFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                if (compressedFile.exists() && compressedFile.length() > 0) {
                    Log.i("ImageUtils", "Compressed file for upload: ${originalFile.length() / 1024} KB -> ${compressedFile.length() / 1024} KB")
                    return compressedFile
                }
            }
            originalFile
        } catch (e: Exception) {
            Log.w("ImageUtils", "Failed to compress file for upload: ${e.message}")
            originalFile
        }
    }

    fun ensurePermanentLocalPath(context: Context, pathOrUri: String?): String? {
        if (pathOrUri.isNullOrBlank()) return null
        val trimmed = pathOrUri.trim()

        if (trimmed.startsWith("content://")) {
            val savedPath = saveCompressedAttachment(context, Uri.parse(trimmed))
            if (savedPath != null) return savedPath
        }

        val cleanPath = if (trimmed.startsWith("file://")) trimmed.substring(7) else trimmed
        val localFile = File(cleanPath)
        if (localFile.exists() && localFile.length() > 0) {
            val attachmentFolder = StorageManager.getAttachmentFolder(context)
            if (!localFile.absolutePath.startsWith(attachmentFolder.absolutePath) || localFile.length() > 300 * 1024) {
                val compressedPath = saveCompressedAttachment(context, Uri.fromFile(localFile))
                if (compressedPath != null) return compressedPath
            }
            return localFile.absolutePath
        }

        return trimmed
    }

    private fun decodeAndScaleBitmapFromStream(getStream: () -> InputStream?, maxDim: Int): Bitmap? {
        return try {
            getStream()?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)

                var scale = 1
                if (options.outHeight > maxDim || options.outWidth > maxDim) {
                    val maxVal = Math.max(options.outHeight, options.outWidth)
                    scale = Math.round(maxVal.toFloat() / maxDim.toFloat()).coerceAtLeast(1)
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
                getStream()?.use { stream2 ->
                    BitmapFactory.decodeStream(stream2, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun rotateBitmapIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                applyExifOrientation(bitmap, orientation)
            } ?: bitmap
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateBitmapIfNeeded(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            applyExifOrientation(bitmap, orientation)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
}
