package g.p.cbb.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import g.p.cbb.data.entity.Customer
import g.p.cbb.repository.SortOption
import g.p.cbb.ui.components.BalanceCard
import g.p.cbb.ui.components.EmptyStateGuidance
import g.p.cbb.utils.BackupManager
import g.p.cbb.utils.DailyBackupWorker
import g.p.cbb.viewmodel.CbbViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: CbbViewModel,
    onCustomerClick: (Customer) -> Unit,
    onHistoryClick: () -> Unit
) {
    val customers by viewModel.customers.collectAsState(initial = emptyList())
    val currentSort by viewModel.sortOption.collectAsState()
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Schedule Daily Backup
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val workRequest = PeriodicWorkRequestBuilder<DailyBackupWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_backup",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    val filteredCustomers = customers.filter { 
        it.name.contains(searchQuery, ignoreCase = true) 
    }

    val totalCredit = customers.filter { it.totalBalance < 0 }.sumOf { -it.totalBalance }
    val totalDebit = customers.filter { it.totalBalance > 0 }.sumOf { it.totalBalance }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search...", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Name (A-Z)") },
                                onClick = { viewModel.updateSortOption(SortOption.NAME); showSortMenu = false },
                                leadingIcon = { if (currentSort == SortOption.NAME) Icon(Icons.Default.Check, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Balance: Low to High") },
                                onClick = { viewModel.updateSortOption(SortOption.BALANCE_LOW_TO_HIGH); showSortMenu = false },
                                leadingIcon = { if (currentSort == SortOption.BALANCE_LOW_TO_HIGH) Icon(Icons.Default.Check, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Balance: High to Low") },
                                onClick = { viewModel.updateSortOption(SortOption.BALANCE_HIGH_TO_LOW); showSortMenu = false },
                                leadingIcon = { if (currentSort == SortOption.BALANCE_HIGH_TO_LOW) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Backup Now") },
                                onClick = {
                                    val success = BackupManager.exportDatabase(context)
                                    Toast.makeText(context, if (success) "Backup saved in udaari/backups" else "Backup failed", Toast.LENGTH_SHORT).show()
                                    showOverflowMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Backup, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore Latest") },
                                onClick = {
                                    viewModel.restoreLatest(context)
                                    Toast.makeText(context, "Checking for latest backup...", Toast.LENGTH_SHORT).show()
                                    showOverflowMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Restore, null) }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddCustomerDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Customer")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BalanceCard(title = "Total Credit", amount = totalCredit, color = Color(0xFF4CAF50))
                BalanceCard(title = "Total Debit", amount = totalDebit, color = Color(0xFFF44336))
            }

            if (customers.isEmpty()) {
                EmptyStateGuidance(
                    icon = Icons.Default.GroupAdd,
                    title = "Get Started",
                    steps = listOf(
                        Icons.Default.Add to "Tap the '+' button at the bottom.",
                        Icons.Default.Person to "Enter customer name and phone.",
                        Icons.Default.ShoppingCart to "Start recording their transactions.",
                        Icons.Default.Warning to "Long-press to mark as Bad Debt (stays at bottom)."
                    )
                )
            } else {
                LazyColumn {
                    items(filteredCustomers) { customer ->
                        val itemAlpha = if (customer.isBadDebt) 0.5f else 1.0f
                        ListItem(
                            headlineContent = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(customer.name)
                                    if (customer.isBadDebt) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("Bad Debt", fontSize = 10.sp) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color.LightGray.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                            },
                            supportingContent = { Text(customer.phone) },
                            trailingContent = {
                                val color = if (customer.totalBalance >= 0) Color(0xFFF44336) else Color(0xFF4CAF50)
                                val label = if (customer.totalBalance < 0) "Advance" else ""
                                Column(horizontalAlignment = Alignment.End) {
                                    if (label.isNotEmpty()) {
                                        Text(label, fontSize = 10.sp, color = color)
                                    }
                                    Text(
                                        text = "₹${"%.2f".format(Math.abs(customer.totalBalance))}",
                                        color = color,
                                        fontSize = 18.sp
                                    )
                                }
                            },
                            modifier = Modifier
                                .alpha(itemAlpha)
                                .combinedClickable(
                                    onClick = { onCustomerClick(customer) },
                                    onLongClick = { customerToEdit = customer }
                                )
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddCustomerDialog) {
        CustomerDialog(
            title = "Add New Customer",
            onDismiss = { showAddCustomerDialog = false },
            onConfirm = { name, phone, address, isBadDebt ->
                viewModel.addCustomer(name, phone, address)
                showAddCustomerDialog = false
            }
        )
    }

    customerToEdit?.let { customer ->
        CustomerDialog(
            title = "Edit Customer",
            initialName = customer.name,
            initialPhone = customer.phone,
            initialAddress = customer.address,
            initialIsBadDebt = customer.isBadDebt,
            onDismiss = { customerToEdit = null },
            onConfirm = { name, phone, address, isBadDebt ->
                viewModel.updateCustomer(customer.copy(name = name, phone = phone, address = address, isBadDebt = isBadDebt))
                customerToEdit = null
            },
            showDelete = true,
            onDelete = {
                viewModel.deleteCustomer(customer)
                customerToEdit = null
            }
        )
    }
}

@Composable
fun CustomerDialog(
    title: String,
    initialName: String = "",
    initialPhone: String = "",
    initialAddress: String = "",
    initialIsBadDebt: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean) -> Unit,
    showDelete: Boolean = false,
    onDelete: () -> Unit = {}
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddress) }
    var isBadDebt by remember { mutableStateOf(initialIsBadDebt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                TextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
                TextField(value = address, onValueChange = { address = it }, label = { Text("Address") })
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isBadDebt, onCheckedChange = { isBadDebt = it })
                    Text("Mark as Bad Debt")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, phone, address, isBadDebt) }) { Text("Confirm") }
        },
        dismissButton = {
            Row {
                if (showDelete) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
