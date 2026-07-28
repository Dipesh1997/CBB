package g.p.cbb.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Data Management",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Keep your records safe. All data is saved in the 'udaari' folder in your Documents.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val success = BackupManager.exportDatabase(context)
                        if (success) viewModel.refreshBackupHistory(context)
                        Toast.makeText(
                            context,
                            if (success) "Backup created in udaari/backups" else "Backup failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup Now")
                }
            }

            Text(
                "Recent Backups",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
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

    showRestoreConfirm?.let { file ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text("Confirm Restore") },
            text = { Text("Restore from ${file.name}? This will overwrite current data.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.restoreSpecific(context, file)
                    Toast.makeText(context, "Restoring...", Toast.LENGTH_SHORT).show()
                    showRestoreConfirm = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) { Text("Cancel") }
            }
        )
    }
}
