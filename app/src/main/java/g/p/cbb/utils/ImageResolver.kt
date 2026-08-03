package g.p.cbb.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.net.URL

object ImageResolver {

    fun resolveImageModel(attachmentPath: String?, driveFileId: String?): Any? {
        val path = attachmentPath?.trim()
        if (!path.isNullOrEmpty()) {
            if (path.startsWith("content://") || path.startsWith("http://") || path.startsWith("https://")) {
                return Uri.parse(path)
            }
            val cleanPath = if (path.startsWith("file://")) path.substring(7) else path
            val file = File(cleanPath)
            if (file.exists() && file.length() > 0) {
                return file
            }
            if (cleanPath.startsWith("/") || path.startsWith("file://")) {
                return Uri.parse(if (path.startsWith("file://")) path else "file://$cleanPath")
            }
        }

        val driveId = driveFileId?.trim()
        if (!driveId.isNullOrEmpty()) {
            return "https://drive.google.com/thumbnail?id=$driveId&sz=w800"
        }

        return null
    }

    fun getLocalFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val clean = if (path.startsWith("file://")) path.substring(7) else path
        val f = File(clean)
        return if (f.exists() && f.length() > 0) f else null
    }

    fun loadBitmap(context: Context, path: String?, driveFileId: String?): Bitmap? {
        if (!path.isNullOrBlank()) {
            try {
                if (path.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(path))?.use { stream ->
                        return BitmapFactory.decodeStream(stream)
                    }
                } else {
                    val cleanPath = if (path.startsWith("file://")) path.substring(7) else path
                    val file = File(cleanPath)
                    if (file.exists() && file.length() > 0) {
                        return BitmapFactory.decodeFile(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val driveId = driveFileId?.trim()
        if (!driveId.isNullOrEmpty()) {
            val urls = listOf(
                "https://drive.google.com/thumbnail?id=$driveId&sz=w800",
                "https://lh3.googleusercontent.com/d/$driveId=w800"
            )
            for (urlStr in urls) {
                try {
                    val conn = URL(urlStr).openConnection().apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    conn.getInputStream().use { stream ->
                        val b = BitmapFactory.decodeStream(stream)
                        if (b != null) return b
                    }
                } catch (e: Exception) {
                    // Try next URL
                }
            }
        }

        return null
    }
}
