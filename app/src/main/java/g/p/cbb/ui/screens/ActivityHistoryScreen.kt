package g.p.cbb.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.History
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
import g.p.cbb.ui.components.EmptyStateGuidance
import g.p.cbb.viewmodel.CbbViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityHistoryScreen(viewModel: CbbViewModel, onBack: () -> Unit) {
    val logs by viewModel.activityLogs.collectAsState(initial = emptyList())
    val tombstones by viewModel.tombstones.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.markHistoryAsRead()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Audit Log & Trash", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Activity Log (${logs.size})") },
                        icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Trash / Restorable (${tombstones.size})") },
                        icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedTabIndex == 0) {
                // Tab 0: Activity Log
                if (logs.isEmpty()) {
                    EmptyStateGuidance(
                        icon = Icons.Default.History,
                        title = "No History",
                        steps = listOf(
                            Icons.Default.History to "Actions like adding customers or transactions will appear here."
                        )
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(logs) { log ->
                            ListItem(
                                headlineContent = { Text(log.description, fontSize = 14.sp) },
                                supportingContent = { Text(dateFormat.format(Date(log.timestamp)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                leadingContent = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                // Tab 1: Trash / Deleted Records
                if (tombstones.isEmpty()) {
                    EmptyStateGuidance(
                        icon = Icons.Default.DeleteForever,
                        title = "Trash is Empty",
                        steps = listOf(
                            Icons.Default.DeleteForever to "Deleted transactions or customers will appear here for easy recovery."
                        )
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tombstones) { ts ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            color = Color(0xFFFFEBEE),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "DELETED ${ts.tableName.uppercase()}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFC62828),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(ts.summary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Deleted: ${dateFormat.format(Date(ts.timestamp))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            viewModel.restoreTombstone(ts)
                                            Toast.makeText(context, "Record reactivated in ledger!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(100.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Restore", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
