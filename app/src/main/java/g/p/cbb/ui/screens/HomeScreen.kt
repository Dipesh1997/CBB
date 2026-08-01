package g.p.cbb.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.data.entity.Customer
import g.p.cbb.repository.SortOption
import g.p.cbb.ui.components.EmptyStateGuidance
import g.p.cbb.viewmodel.CbbViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: CbbViewModel,
    onCustomerClick: (Customer) -> Unit
) {
    val customers by viewModel.customers.collectAsState(initial = emptyList())
    val currentSort by viewModel.sortOption.collectAsState()
    
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val filteredCustomers = customers.filter { 
        it.name.contains(searchQuery, ignoreCase = true) 
    }

    val totalCredit = customers.filter { it.totalBalance < 0 }.sumOf { abs(it.totalBalance) }
    val totalDebit = customers.filter { it.totalBalance > 0 }.sumOf { it.totalBalance }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search Customers...", fontSize = 14.sp) },
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
                    IconButton(onClick = { 
                        Toast.makeText(context, "Syncing with Cloud...", Toast.LENGTH_SHORT).show()
                        viewModel.syncNow()
                    }) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Sync",
                            tint = Color(0xFF4CAF50)
                        )
                    }
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
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddCustomerDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Customer")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Business Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFF44336).copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Receivable", fontSize = 12.sp, color = Color(0xFFF44336))
                                Text("₹${"%.2f".format(totalDebit)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Advance", fontSize = 12.sp, color = Color(0xFF4CAF50))
                                Text("₹${"%.2f".format(totalCredit)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Customer List (${filteredCustomers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (customers.isEmpty()) {
                item {
                    EmptyStateGuidance(
                        icon = Icons.Default.GroupAdd,
                        title = "No Customers Found",
                        steps = listOf(
                            Icons.Default.Add to "Tap the '+' button to add your first customer.",
                            Icons.Default.CloudDownload to "Tap the cloud icon to pull data from Sheets.",
                            Icons.Default.TouchApp to "Click a customer to manage their ledger."
                        )
                    )
                }
            } else {
                items(filteredCustomers) { customer ->
                    val itemAlpha = if (customer.isBadDebt) 0.5f else 1.0f
                    ListItem(
                        headlineContent = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(customer.name, fontWeight = FontWeight.Bold)
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
                        supportingContent = { 
                            Text(customer.phone)
                        },
                        trailingContent = {
                            val color = if (customer.totalBalance >= 0) Color(0xFFF44336) else Color(0xFF4CAF50)
                            Text(
                                text = "₹${"%.2f".format(abs(customer.totalBalance))}",
                                color = color,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        },
                        modifier = Modifier
                            .alpha(itemAlpha)
                            .combinedClickable(
                                onClick = { onCustomerClick(customer) },
                                onLongClick = { customerToEdit = customer }
                            )
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
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
            title = "Edit Customer Details",
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isBadDebt, onCheckedChange = { isBadDebt = it })
                    Text("Mark as Bad Debt")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, phone, address, isBadDebt) }) { 
                Text(if (showDelete) "Update" else "Add") 
            }
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
