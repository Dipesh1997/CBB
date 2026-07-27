package g.p.cbb.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.data.entity.Customer
import g.p.cbb.ui.components.BalanceCard
import g.p.cbb.ui.components.EmptyStateGuidance
import g.p.cbb.viewmodel.CbbViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: CbbViewModel,
    onCustomerClick: (Customer) -> Unit,
    onHistoryClick: () -> Unit
) {
    val customers by viewModel.customers.collectAsState(initial = emptyList())
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var searchQuery by remember { mutableStateOf("") }

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
                        placeholder = { Text("Search Customers...") },
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                        ),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, contentDescription = "History")
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
                        Icons.Default.ShoppingCart to "Start recording their transactions."
                    )
                )
            } else {
                LazyColumn {
                    items(filteredCustomers) { customer ->
                        ListItem(
                            headlineContent = { Text(customer.name) },
                            supportingContent = { Text(customer.phone) },
                            trailingContent = {
                                val color = if (customer.totalBalance >= 0) Color(0xFFF44336) else Color(0xFF4CAF50)
                                val label = if (customer.totalBalance < 0) "Advance" else ""
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
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
                            modifier = Modifier.combinedClickable(
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
            onConfirm = { name, phone, address ->
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
            onDismiss = { customerToEdit = null },
            onConfirm = { name, phone, address ->
                viewModel.updateCustomer(customer.copy(name = name, phone = phone, address = address))
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
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
    showDelete: Boolean = false,
    onDelete: () -> Unit = {}
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddress) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                TextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
                TextField(value = address, onValueChange = { address = it }, label = { Text("Address") })
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, phone, address) }) { Text("Confirm") }
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
