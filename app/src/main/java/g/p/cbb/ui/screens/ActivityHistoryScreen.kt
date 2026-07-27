package g.p.cbb.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            EmptyStateGuidance(
                icon = Icons.Default.History,
                title = "No History",
                steps = listOf(
                    Icons.Default.History to "Actions like adding customers or transactions will appear here."
                ),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(logs) { log ->
                    ListItem(
                        headlineContent = { Text(log.description) },
                        supportingContent = { Text(dateFormat.format(Date(log.timestamp)), fontSize = 12.sp) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
