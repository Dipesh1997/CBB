package g.p.cbb.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import g.p.cbb.data.entity.Customer
import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import g.p.cbb.ui.components.AddEditTransactionDialog
import g.p.cbb.ui.components.ConfirmDeleteDialog
import g.p.cbb.ui.components.FullScreenImageViewer
import g.p.cbb.ui.components.SyncStatusBadge
import g.p.cbb.viewmodel.CbbViewModel
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    viewModel: CbbViewModel,
    onCustomerClick: (Customer) -> Unit
) {
    val customers by viewModel.customers.collectAsState(initial = emptyList())
    val transactions by viewModel.transactions.collectAsState()
    val unreadTxCount by viewModel.unreadTransactionsCount.collectAsState(initial = 0)
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    LaunchedEffect(Unit) {
        viewModel.markTransactionsAsViewed()
    }

    val filteredTransactions = transactions.filter { tx ->
        val cust = customers.find { it.id == tx.customerId }
        val custName = cust?.name ?: ""
        custName.contains(searchQuery, ignoreCase = true) || tx.note.contains(searchQuery, ignoreCase = true)
    }.sortedByDescending { it.timestamp }

    val notificationsEnabled by viewModel.areNotificationsEnabled.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Transaction History", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(
                            onClick = {
                                val newState = !notificationsEnabled
                                viewModel.toggleNotifications(newState)
                                val msg = if (newState) "System notifications enabled" else "System notifications stopped"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = if (notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                contentDescription = "Toggle Notifications",
                                tint = if (notificationsEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        SyncStatusBadge(
                            isSyncing = false,
                            isError = false,
                            lastSyncText = "Live Auto-Sync",
                            onManualSync = { viewModel.syncNow() }
                        )
                    }
                )
                if (unreadTxCount > 0) {
                    Surface(
                        color = Color(0xFFE3F2FD),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF0288D1), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "✨ $unreadTxCount new update(s)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0288D1)
                                )
                            }
                            TextButton(
                                onClick = { viewModel.markTransactionsAsViewed() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = "Mark Read", modifier = Modifier.size(16.dp), tint = Color(0xFF0288D1))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mark Read", fontSize = 12.sp, color = Color(0xFF0288D1), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by customer name or note...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(25.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredTransactions, key = { it.id }) { tx ->
                    val cust = customers.find { it.id == tx.customerId }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { transactionToEdit = tx },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cust?.name ?: "Unknown Customer",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        cust?.let { onCustomerClick(it) }
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateFormatter.format(Date(tx.timestamp)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (tx.note.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tx.note,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (tx.type == TransactionType.DEBIT) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = tx.type.name,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tx.type == TransactionType.DEBIT) Color(0xFFB71C1C) else Color(0xFF1B5E20),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currencyFormatter.format(tx.amount),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (tx.type == TransactionType.DEBIT) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                     val imageModel: Any? = remember(tx.attachmentPath, tx.driveFileId) {
                                         g.p.cbb.utils.ImageResolver.resolveImageModel(tx.attachmentPath, tx.driveFileId)
                                     }

                                    if (imageModel != null) {
                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = "Receipt",
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable { previewImagePath = imageModel.toString() },
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val cust = customers.find { it.id == tx.customerId }
                                             if (cust != null) {
                                                 val linkedPayments = transactions.filter { it.parentTransactionId == tx.id }
                                                 g.p.cbb.utils.ImageGenerator.shareBillImage(
                                                     context = context,
                                                     customer = cust,
                                                     bill = tx,
                                                     payments = linkedPayments
                                                 )
                                             }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share Bill Image",
                                            tint = Color(0xFF0288D1),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { transactionToEdit = tx },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = { transactionToDelete = tx },
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
    }

    // Add / Edit Dialog
    if (showAddDialog || transactionToEdit != null) {
        AddEditTransactionDialog(
            customers = customers,
            transactionToEdit = transactionToEdit,
            onDismiss = {
                showAddDialog = false
                transactionToEdit = null
            },
            onConfirm = { customerId, amount, type, note, photoUri, timestamp ->
                if (transactionToEdit == null) {
                    val cust = customers.find { it.id == customerId }
                    if (cust != null) {
                        val permanentPath = g.p.cbb.utils.ImageUtils.ensurePermanentLocalPath(context, photoUri?.toString())
                        viewModel.selectCustomer(cust)
                        viewModel.addTransaction(
                            amount = amount,
                            type = type,
                            note = note,
                            timestamp = timestamp,
                            attachmentPath = permanentPath
                        )
                    }
                } else {
                    val permanentPath = g.p.cbb.utils.ImageUtils.ensurePermanentLocalPath(context, photoUri?.toString() ?: transactionToEdit!!.attachmentPath)
                    viewModel.updateTransaction(
                        oldTransaction = transactionToEdit!!,
                        newTransaction = transactionToEdit!!.copy(
                            customerId = customerId,
                            amount = amount,
                            type = type,
                            note = note,
                            timestamp = timestamp,
                            attachmentPath = permanentPath
                        )
                    )
                }
                showAddDialog = false
                transactionToEdit = null
            }
        )
    }

    // Confirm Delete Dialog
    if (transactionToDelete != null) {
        ConfirmDeleteDialog(
            title = "Delete Transaction Record",
            message = "Are you sure you want to delete this ${transactionToDelete!!.type.name} entry of ${currencyFormatter.format(transactionToDelete!!.amount)}?",
            onDismiss = { transactionToDelete = null },
            onConfirm = {
                viewModel.deleteTransaction(transactionToDelete!!)
                transactionToDelete = null
            }
        )
    }

    if (previewImagePath != null) {
        FullScreenImageViewer(
            imagePath = previewImagePath!!,
            onDismiss = { previewImagePath = null }
        )
    }
}

