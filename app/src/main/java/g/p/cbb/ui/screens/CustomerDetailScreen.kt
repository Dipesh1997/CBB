package g.p.cbb.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import g.p.cbb.ui.components.*
import g.p.cbb.ui.theme.*
import g.p.cbb.utils.PdfDetailLevel
import g.p.cbb.utils.PdfGenerator
import g.p.cbb.viewmodel.CbbViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    viewModel: CbbViewModel,
    customer: Customer,
    onBack: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val context = LocalContext.current

    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    LaunchedEffect(customer.id) {
        viewModel.selectCustomer(customer)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "${customer.name}'s Ledger", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val details = viewModel.getAllTransactionsWithDetails()
                            PdfGenerator.generateCustomerLedger(
                                context = context,
                                customer = customer,
                                transactions = details,
                                detailLevel = PdfDetailLevel.DETAILED
                            )
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export PDF", fontSize = 12.sp)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTransactionDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
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
            // Customer Ledger Info Box
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Phone: ${customer.phone}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (customer.address.isNotBlank()) {
                                Text("Address: ${customer.address}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Balance", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = currencyFormatter.format(customer.totalBalance),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = if (customer.totalBalance >= 0) AdvanceText else ReceivableText
                            )
                        }
                    }
                }
            }

            // Customer Specific Warning Box
            item {
                if (customer.isBadDebt) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEE2E2), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFDC2626).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                            Column {
                                Text("Bad Debt Account Warning", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF991B1B))
                                Text("This customer is marked for default risk. Exercise caution when extending credit.", fontSize = 11.sp, color = Color(0xFF991B1B))
                            }
                        }
                    }
                } else if (customer.totalBalance >= 10000) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WarningContainer, RoundedCornerShape(12.dp))
                            .border(1.dp, Warning.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(22.dp))
                            Column {
                                Text("High Overdue Balance", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OnWarningContainer)
                                Text("Customer balance is high (${currencyFormatter.format(customer.totalBalance)}). Consider requesting settlement.", fontSize = 11.sp, color = OnWarningContainer)
                            }
                        }
                    }
                }
            }

            // Transactions Header
            item {
                Text(
                    text = "Transactions History (${transactions.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Transactions List
            items(transactions, key = { it.id }) { tx ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = dateFormatter.format(Date(tx.timestamp)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

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
                            }

                            if (tx.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tx.note,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = currencyFormatter.format(tx.amount),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (tx.type == TransactionType.DEBIT) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                            )

                            if (tx.attachmentPath != null || tx.driveFileId != null) {
                                val imageUrl = tx.attachmentPath ?: "https://drive.google.com/thumbnail?id=${tx.driveFileId}&sz=w200"
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Receipt",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
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

    // Add / Edit Transaction Dialog
    if (showAddTransactionDialog || transactionToEdit != null) {
        AddEditTransactionDialog(
            customers = listOf(customer),
            preselectedCustomerId = customer.id,
            transactionToEdit = transactionToEdit,
            onDismiss = {
                showAddTransactionDialog = false
                transactionToEdit = null
            },
            onConfirm = { _, amount, type, note, photoUri ->
                if (transactionToEdit == null) {
                    viewModel.addTransaction(
                        amount = amount,
                        type = type,
                        note = note,
                        attachmentPath = photoUri?.toString()
                    )
                } else {
                    viewModel.updateTransaction(
                        oldTransaction = transactionToEdit!!,
                        newTransaction = transactionToEdit!!.copy(
                            amount = amount,
                            type = type,
                            note = note,
                            attachmentPath = photoUri?.toString() ?: transactionToEdit!!.attachmentPath
                        )
                    )
                }
                showAddTransactionDialog = false
                transactionToEdit = null
            }
        )
    }

    // Confirm Delete Dialog
    if (transactionToDelete != null) {
        ConfirmDeleteDialog(
            title = "Delete Transaction Record",
            message = "Are you sure you want to delete this ${transactionToDelete!!.type.name} entry of ${currencyFormatter.format(transactionToDelete!!.amount)}? Customer balance will be updated automatically.",
            onDismiss = { transactionToDelete = null },
            onConfirm = {
                viewModel.deleteTransaction(transactionToDelete!!)
                transactionToDelete = null
            }
        )
    }
}
