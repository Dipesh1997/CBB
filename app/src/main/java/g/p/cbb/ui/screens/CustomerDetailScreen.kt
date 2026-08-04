package g.p.cbb.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import g.p.cbb.data.entity.Customer
import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import g.p.cbb.ui.components.*
import g.p.cbb.ui.theme.*
import g.p.cbb.utils.PdfDetailLevel
import g.p.cbb.utils.PdfGenerator
import g.p.cbb.utils.ReminderManager
import g.p.cbb.viewmodel.CbbViewModel
import java.io.File
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
    val selectedCustomerState by viewModel.selectedCustomer.collectAsState()
    val currentCustomer = selectedCustomerState ?: customer
    val customerTransactions = remember(transactions, currentCustomer.id) {
        transactions.filter { it.customerId == currentCustomer.id }
    }
    val context = LocalContext.current

    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showExportPdfDialog by remember { mutableStateOf(false) }
    var showCustomerPdfsDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var parentTransactionForPayment by remember { mutableStateOf<Transaction?>(null) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showQuickBillDialog by remember { mutableStateOf(false) }
    var showEditCustomerDialog by remember { mutableStateOf(false) }

    // Temp file URI for camera capture
    val cameraPhotoFile = remember {
        File(context.cacheDir, "quick_bill_${System.currentTimeMillis()}.jpg")
    }
    val cameraPhotoFileUri: Uri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", cameraPhotoFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val savedPath = g.p.cbb.utils.ImageUtils.saveCompressedAttachment(context, cameraPhotoFileUri)
            capturedPhotoUri = if (savedPath != null) Uri.fromFile(File(savedPath)) else cameraPhotoFileUri
            showQuickBillDialog = true
        }
    }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    LaunchedEffect(customer.id) {
        viewModel.selectCustomer(customer)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "${currentCustomer.name}'s Ledger", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditCustomerDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Customer", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showCustomerPdfsDialog = true }) {
                        Icon(Icons.Default.FolderZip, contentDescription = "Saved PDFs", tint = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = { showExportPdfDialog = true },
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Camera quick-capture FAB
                ExtendedFloatingActionButton(
                    onClick = { cameraLauncher.launch(cameraPhotoFileUri) },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    text = { Text("Capture Bill", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                // Manual add-bill FAB
                ExtendedFloatingActionButton(
                    onClick = { showAddTransactionDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Bill", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Customer Ledger Info Box
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CustomerAvatar(
                                name = currentCustomer.name,
                                profileImageUri = currentCustomer.profileImageUri,
                                size = 48.dp,
                                onClick = {
                                    if (!currentCustomer.profileImageUri.isNullOrBlank()) {
                                        previewImagePath = currentCustomer.profileImageUri
                                    }
                                }
                            )

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Phone: ${currentCustomer.phone}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (currentCustomer.phone.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:${currentCustomer.phone}"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Color(0xFFE8F5E9), androidx.compose.foundation.shape.CircleShape)
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = "Call Customer", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                if (currentCustomer.address.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Address: ${currentCustomer.address}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Balance", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = currencyFormatter.format(currentCustomer.totalBalance),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = if (currentCustomer.totalBalance >= 0) AdvanceText else ReceivableText
                            )
                        }
                    }
                }
            }

            // Payment Reminder Box
            item {
                val reminderFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val activeReminder = currentCustomer.reminderTime?.takeIf { it > System.currentTimeMillis() }

                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (activeReminder != null) (if (isDark) ReceivableBgDark else Color(0xFFE8F5E9)) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (activeReminder != null) Icons.Default.NotificationsActive else Icons.Default.AddAlarm,
                                contentDescription = null,
                                tint = if (activeReminder != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = if (activeReminder != null) "Follow-up Reminder Set" else "Set Payment Reminder",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (activeReminder != null) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (activeReminder != null)
                                        reminderFormat.format(Date(activeReminder))
                                    else
                                        "Tap to pick date & time for payment reminder",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = {
                                    showDateTimePicker(context) { selectedMillis ->
                                        val delay = selectedMillis - System.currentTimeMillis()
                                        if (delay > 0) {
                                            viewModel.setReminder(selectedMillis)
                                            ReminderManager.scheduleReminder(context, currentCustomer.name, delay)
                                            Toast.makeText(context, "Reminder set for ${reminderFormat.format(Date(selectedMillis))}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Please pick a future date and time", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(if (activeReminder != null) "Change" else "Set Date", fontSize = 11.sp)
                            }

                            if (activeReminder != null) {
                                IconButton(
                                    onClick = {
                                        viewModel.cancelReminder()
                                        Toast.makeText(context, "Reminder cancelled", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Cancel Reminder", tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
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
                    text = "Transactions History (${customerTransactions.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Transactions List
            items(customerTransactions, key = { it.id }) { tx ->
                val linkedPayments = remember(customerTransactions, tx.id) {
                    customerTransactions.filter { it.parentTransactionId == tx.id }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { transactionToEdit = tx },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

                            if (tx.type == TransactionType.DEBIT && linkedPayments.isNotEmpty()) {
                                val totalReceived = linkedPayments.sumOf { it.amount }
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Received: ${currencyFormatter.format(totalReceived)} (${linkedPayments.size} part payment)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currencyFormatter.format(tx.amount),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (tx.type == TransactionType.DEBIT) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                            )

                            val imageModel: Any? = remember(tx.attachmentPath, tx.driveFileId) {
                                g.p.cbb.utils.ImageResolver.resolveImageModel(tx.attachmentPath, tx.driveFileId)
                            }

                            if (imageModel != null) {
                                AsyncImage(
                                    model = imageModel,
                                    contentDescription = "Receipt",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { previewImagePath = imageModel.toString() },
                                    contentScale = ContentScale.Crop
                                )
                            }

                            if (tx.type == TransactionType.DEBIT) {
                                IconButton(
                                    onClick = { parentTransactionForPayment = tx },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = "Record Part Payment",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    g.p.cbb.utils.ImageGenerator.shareBillImage(
                                        context = context,
                                        customer = customer,
                                        bill = tx,
                                        payments = linkedPayments
                                    )
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

    if (showExportPdfDialog) {
        ExportPdfDialog(
            customer = currentCustomer,
            transactions = viewModel.getTransactionsWithDetailsForCustomer(currentCustomer.id),
            onDismiss = { showExportPdfDialog = false }
        )
    }

    if (showCustomerPdfsDialog) {
        CustomerPdfsDialog(
            customer = customer,
            onDismiss = { showCustomerPdfsDialog = false },
            onGenerateNewPdf = { showExportPdfDialog = true }
        )
    }

    // Quick Bill Dialog (after camera capture)
    if (showQuickBillDialog && capturedPhotoUri != null) {
        QuickBillDialog(
            capturedPhotoUri = capturedPhotoUri!!,
            onDismiss = {
                showQuickBillDialog = false
                capturedPhotoUri = null
            },
            onConfirm = { amount, type, note, photoUri ->
                val permanentPath = g.p.cbb.utils.ImageUtils.ensurePermanentLocalPath(context, photoUri.toString())
                viewModel.addTransaction(
                    amount = amount,
                    type = type,
                    note = note,
                    timestamp = System.currentTimeMillis(),
                    attachmentPath = permanentPath,
                    parentTransactionId = null
                )
                showQuickBillDialog = false
                capturedPhotoUri = null
            }
        )
    }

    // Add / Edit / Part Payment Dialog
    if (showAddTransactionDialog || transactionToEdit != null || parentTransactionForPayment != null) {
        AddEditTransactionDialog(
            customers = listOf(customer),
            preselectedCustomerId = customer.id,
            parentTransactionId = parentTransactionForPayment?.id,
            transactionToEdit = transactionToEdit,
            onDismiss = {
                showAddTransactionDialog = false
                transactionToEdit = null
                parentTransactionForPayment = null
            },
            onConfirm = { _, amount, type, note, photoUri, timestamp ->
                if (transactionToEdit == null) {
                    val permanentPath = g.p.cbb.utils.ImageUtils.ensurePermanentLocalPath(context, photoUri?.toString())
                    viewModel.addTransaction(
                        amount = amount,
                        type = type,
                        note = note,
                        timestamp = timestamp,
                        attachmentPath = permanentPath,
                        parentTransactionId = parentTransactionForPayment?.id
                    )
                } else {
                    val permanentPath = g.p.cbb.utils.ImageUtils.ensurePermanentLocalPath(context, photoUri?.toString() ?: transactionToEdit!!.attachmentPath)
                    viewModel.updateTransaction(
                        oldTransaction = transactionToEdit!!,
                        newTransaction = transactionToEdit!!.copy(
                            amount = amount,
                            type = type,
                            note = note,
                            timestamp = timestamp,
                            attachmentPath = permanentPath
                        )
                    )
                }
                showAddTransactionDialog = false
                transactionToEdit = null
                parentTransactionForPayment = null
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

    if (showEditCustomerDialog) {
        AddEditCustomerDialog(
            customerToEdit = currentCustomer,
            onDismiss = { showEditCustomerDialog = false },
            onConfirm = { name, phone, address, profileImageUri, isBadDebt ->
                viewModel.updateCustomer(
                    currentCustomer.copy(
                        name = name,
                        phone = phone,
                        address = address,
                        profileImageUri = profileImageUri,
                        isBadDebt = isBadDebt
                    )
                )
                showEditCustomerDialog = false
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

private fun showDateTimePicker(context: android.content.Context, onDateTimeSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance()
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val timePickerDialog = android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    onDateTimeSelected(calendar.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
            )
            timePickerDialog.show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
    datePickerDialog.show()
}
