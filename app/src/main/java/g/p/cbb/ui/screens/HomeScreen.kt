package g.p.cbb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.data.entity.Customer
import g.p.cbb.ui.components.*
import g.p.cbb.ui.theme.*
import g.p.cbb.viewmodel.CbbViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: CbbViewModel,
    onCustomerClick: (Customer) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToCollaboration: () -> Unit = {}
) {
    val customers by viewModel.customers.collectAsState(initial = emptyList())
    val userEmail by viewModel.userEmail.collectAsState()
    val availableAccounts by viewModel.availableAccounts.collectAsState()
    val showAccountPickerDialog by viewModel.showAccountPickerDialog.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var customerToDelete by remember { mutableStateOf<Customer?>(null) }

    if (showAccountPickerDialog) {
        AccountSelectionDialog(
            currentEmail = userEmail,
            availableAccounts = availableAccounts,
            onAccountSelected = { email -> viewModel.selectAccount(email) },
            onAddAccountClick = {
                viewModel.dismissAccountPicker()
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_ADD_ACCOUNT).apply {
                        putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    viewModel.pickAccount()
                }
            },
            onDismiss = { viewModel.dismissAccountPicker() }
        )
    }

    val filteredCustomers = customers.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery)
    }

    val totalReceivable = customers.filter { it.totalBalance > 0 }.sumOf { it.totalBalance }
    val totalAdvance = customers.filter { it.totalBalance < 0 }.sumOf { kotlin.math.abs(it.totalBalance) }
    val badDebtCount = customers.count { it.isBadDebt }
    val highOverdueCount = customers.count { !it.isBadDebt && it.totalBalance >= 10000 }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable { viewModel.openAccountPicker(context) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Udaari Ledger",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = userEmail ?: "Tap to select account",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Switch Account",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    SyncStatusBadge(
                        isSyncing = false,
                        isError = false,
                        lastSyncText = "Live Auto-Sync",
                        onManualSync = {
                            if (userEmail.isNullOrBlank()) {
                                viewModel.openAccountPicker(context)
                            } else {
                                viewModel.syncNow()
                            }
                        }
                    )
                    IconButton(onClick = onNavigateToCollaboration) {
                        Icon(Icons.Default.Group, contentDescription = "Team Collaboration & Invites", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "Activity Log")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddCustomerDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stats Grid
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Receivables Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = ReceivableBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Total Receivables", fontSize = 12.sp, color = ReceivableText)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currencyFormatter.format(totalReceivable),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = ReceivableText
                            )
                        }
                    }

                    // Advances Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = AdvanceBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Total Advances", fontSize = 12.sp, color = AdvanceText)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currencyFormatter.format(totalAdvance),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = AdvanceText
                            )
                        }
                    }
                }
            }

            // Risk Alert Banner
            item {
                RiskWarningBanner(
                    badDebtCount = badDebtCount,
                    highOverdueCount = highOverdueCount
                )
            }

            // Tip of the Day Card
            item {
                TipsCard()
            }

            // Search Bar & Customer Count Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customers (${filteredCustomers.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .width(180.dp)
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    )
                }
            }

            // Customer List
            items(filteredCustomers, key = { it.id }) { customer ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCustomerClick(customer) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = customer.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Status Badge
                                when {
                                    customer.isBadDebt -> {
                                        Surface(
                                            color = Color(0xFFFEE2E2),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "BAD DEBT",
                                                color = Color(0xFF991B1B),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    customer.totalBalance >= 10000 -> {
                                        Surface(
                                            color = WarningContainer,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "HIGH OVERDUE",
                                                color = OnWarningContainer,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = customer.phone,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = currencyFormatter.format(customer.totalBalance),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (customer.totalBalance >= 0) AdvanceText else ReceivableText
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { customerToEdit = customer },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { customerToDelete = customer },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddCustomerDialog || customerToEdit != null) {
        AddEditCustomerDialog(
            customerToEdit = customerToEdit,
            onDismiss = {
                showAddCustomerDialog = false
                customerToEdit = null
            },
            onConfirm = { name, phone, address, isBadDebt ->
                if (customerToEdit == null) {
                    viewModel.addCustomer(name, phone, address)
                } else {
                    viewModel.updateCustomer(customerToEdit!!.copy(name = name, phone = phone, address = address, isBadDebt = isBadDebt))
                }
                showAddCustomerDialog = false
                customerToEdit = null
            }
        )
    }

    // Confirm Delete Dialog
    if (customerToDelete != null) {
        ConfirmDeleteDialog(
            title = "Delete Customer Record",
            message = "Are you sure you want to delete ${customerToDelete!!.name}? All transaction entries will be moved to Trash.",
            onDismiss = { customerToDelete = null },
            onConfirm = {
                viewModel.deleteCustomer(customerToDelete!!)
                customerToDelete = null
            }
        )
    }
}
