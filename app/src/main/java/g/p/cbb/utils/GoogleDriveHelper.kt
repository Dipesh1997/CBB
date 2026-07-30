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
        
        return uploadedFile.id
    }

    fun shareWithUser(drive: Drive, fileId: String, email: String) {
        val permission = Permission()
            .setType("user")
            .setRole("writer")
            .setEmailAddress(email)
        
        drive.permissions().create(fileId, permission).execute()
    }
}
