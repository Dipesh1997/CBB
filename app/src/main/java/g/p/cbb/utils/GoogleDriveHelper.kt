package g.p.cbb.utils

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import java.io.File as JavaFile

object GoogleDriveHelper {

    fun uploadFile(drive: Drive, localFile: JavaFile, folderId: String?): String {
        val fileMetadata = File()
        fileMetadata.name = localFile.name
        if (folderId != null) fileMetadata.parents = listOf(folderId)

        val mediaContent = com.google.api.client.http.FileContent("image/jpeg", localFile)
        val uploadedFile = drive.files().create(fileMetadata, mediaContent)
            .setFields("id")
            .execute()
        
        // Make the uploaded image file readable to everyone so collaborators can view attachments without asking for grant access
        try {
            val permission = Permission()
                .setType("anyone")
                .setRole("reader")
            drive.permissions().create(uploadedFile.id, permission).execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return uploadedFile.id
    }

    fun shareWithUser(drive: Drive, fileId: String, email: String) {
        val permission = Permission()
            .setType("user")
            .setRole("writer")
            .setEmailAddress(email)
        
        drive.permissions().create(fileId, permission).execute()
    }

    fun findFileByName(drive: Drive, name: String, mimeType: String): String? {
        return try {
            val query = "name = '$name' and mimeType = '$mimeType' and trashed = false"
            val result = drive.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .execute()
            result.files?.firstOrNull()?.id
        } catch (e: Exception) {
            null
        }
    }

    fun createFolder(drive: Drive, name: String): String {
        val folderMetadata = File().apply {
            this.name = name
            mimeType = "application/vnd.google-apps.folder"
        }
        val created = drive.files().create(folderMetadata)
            .setFields("id")
            .execute()
        
        try {
            val permission = Permission()
                .setType("anyone")
                .setRole("reader")
            drive.permissions().create(created.id, permission).execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return created.id
    }

    fun createSpreadsheet(drive: Drive, name: String): String {
        val fileMetadata = File().apply {
            this.name = name
            mimeType = "application/vnd.google-apps.spreadsheet"
        }
        val created = drive.files().create(fileMetadata)
            .setFields("id")
            .execute()
        return created.id
    }
}
