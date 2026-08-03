package g.p.cbb.ui.components

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import g.p.cbb.data.entity.Customer
import g.p.cbb.utils.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun getCustomerPdfFiles(context: Context, customerName: String): List<File> {
    val result = mutableListOf<File>()
    val sanitizedName = customerName.replace("\\s+".toRegex(), "_").lowercase()
    val nameParts = customerName.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

    val foldersToSearch = listOf(
        StorageManager.getStatementFolder(context),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "udaari/statements"),
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, "udaari/statements") }
    ).filterNotNull()

    val seenPaths = mutableSetOf<String>()

    foldersToSearch.forEach { folder ->
        if (folder.exists() && folder.isDirectory) {
            folder.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".pdf", ignoreCase = true) && file.path !in seenPaths) {
                    val lowerName = file.name.lowercase()
                    val matchesName = lowerName.contains(sanitizedName) || nameParts.all { lowerName.contains(it) }
                    if (matchesName) {
                        seenPaths.add(file.path)
                        result.add(file)
                    }
                }
            }
        }
    }

    return result.sortedByDescending { it.lastModified() }
}

fun sharePdfFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Statement via"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun CustomerPdfsDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onGenerateNewPdf: () -> Unit
) {
    val context = LocalContext.current
    var pdfFiles by remember { mutableStateOf(getCustomerPdfFiles(context, customer.name)) }
    var selectedPdfForViewing by remember { mutableStateOf<File?>(null) }
    var pdfToDelete by remember { mutableStateOf<File?>(null) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${customer.name}'s Saved PDFs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (pdfFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "No saved PDF statements found for ${customer.name}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onGenerateNewPdf()
                                },
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export New PDF Statement", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pdfFiles, key = { it.path }) { file ->
                            val fileSizeKb = file.length() / 1024
                            val sizeText = if (fileSizeKb > 1024) "%.1f MB".format(fileSizeKb / 1024f) else "$fileSizeKb KB"

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPdfForViewing = file },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFFFEBEE)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = Color(0xFFB71C1C),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = file.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${dateFormatter.format(Date(file.lastModified()))} • $sizeText",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { selectedPdfForViewing = file },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "View PDF",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { sharePdfFile(context, file) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share PDF",
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { pdfToDelete = file },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete PDF",
                                                tint = Color.Red,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (selectedPdfForViewing != null) {
        PdfViewer(
            file = selectedPdfForViewing!!,
            onDismiss = { selectedPdfForViewing = null },
            onShare = { sharePdfFile(context, selectedPdfForViewing!!) },
            onDelete = {
                pdfToDelete = selectedPdfForViewing
            }
        )
    }

    if (pdfToDelete != null) {
        ConfirmDeleteDialog(
            title = "Delete PDF Statement",
            message = "Are you sure you want to delete ${pdfToDelete!!.name}? This file will be permanently removed.",
            onDismiss = { pdfToDelete = null },
            onConfirm = {
                val f = pdfToDelete!!
                if (f.exists()) f.delete()
                pdfFiles = getCustomerPdfFiles(context, customer.name)
                if (selectedPdfForViewing == f) selectedPdfForViewing = null
                pdfToDelete = null
                Toast.makeText(context, "PDF Statement deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
