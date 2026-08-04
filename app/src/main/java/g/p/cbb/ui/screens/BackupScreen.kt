package g.p.cbb.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.utils.BackupManager
import g.p.cbb.viewmodel.CbbViewModel

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(viewModel: CbbViewModel) {
    val context = LocalContext.current
    var showRestoreConfirm by remember { mutableStateOf<File?>(null) }
    var showCloudRestoreConfirm by remember { mutableStateOf(false) }
    val history by viewModel.backupHistory.collectAsState()
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    LaunchedEffect(Unit) {
        viewModel.refreshBackupHistory(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Backup & Restore") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Local Backup Section
            item {
                Column(modifier = Modifier.padding(24.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Local Data Management",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Save a copy of your database locally on this phone. Backups are stored in 'Documents/udaari/backups'.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val success = BackupManager.exportDatabase(context)
                            if (success) viewModel.refreshBackupHistory(context)
                            Toast.makeText(
                                context,
                                if (success) "Backup created in Documents/udaari/backups" else "Backup failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup to Phone Storage")
                    }
                }
            }

            // Cloud Restore Section (Web Parity)
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Cloud Synchronization",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Restore data directly from your linked Google Sheet. Use this to sync data from the web or other devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showCloudRestoreConfirm = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Text("Restore from Cloud")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Recent Local Backups",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }

            if (history.isEmpty()) {
                item {
                    Text(
                        "No local backups found.",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            } else {
                items(history) { file ->
                    ListItem(
                        headlineContent = { Text(file.name) },
                        supportingContent = { 
                            val size = file.length() / 1024
                            Text("${dateFormat.format(Date(file.lastModified()))} • ${size}KB")
                        },
                        trailingContent = {
                            IconButton(onClick = { showRestoreConfirm = file }) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore")
                            }
                        },
                        modifier = Modifier.clickable { showRestoreConfirm = file }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCloudRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showCloudRestoreConfirm = false },
            title = { Text("Confirm Cloud Restore") },
            text = { Text("This will pull all data from your Google Sheet and update your local ledger. Local records will be overwritten if they are different from the cloud. Proceed?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.restoreFromCloud()
                    showCloudRestoreConfirm = false
                }) { Text("Restore Now") }
            },
            dismissButton = {
                TextButton(onClick = { showCloudRestoreConfirm = false }) { Text("Cancel") }
            }
        )
    }

    showRestoreConfirm?.let { file ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text("Confirm Local Restore") },
            text = { Text("Restore from ${file.name}? This will overwrite current data.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.restoreSpecific(context, file)
                    Toast.makeText(context, "Restoring local backup...", Toast.LENGTH_SHORT).show()
                    showRestoreConfirm = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) { Text("Cancel") }
            }
        )
    }
}
