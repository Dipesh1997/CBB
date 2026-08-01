package g.p.cbb.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import g.p.cbb.ui.components.EmptyStateGuidance
import g.p.cbb.ui.components.FullScreenImageViewer
import g.p.cbb.ui.components.PdfViewer
import g.p.cbb.utils.*
import g.p.cbb.viewmodel.CbbViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CustomerDetailScreen(viewModel: CbbViewModel, onBack: () -> Unit) {
    val customer by viewModel.selectedCustomer.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedTransactionIds.collectAsState()

    var showAddTransactionDialog by remember { mutableStateOf<TransactionType?>(null) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var selectedTransactionIdForDetails by remember { mutableStateOf<Long?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showPartPaymentDialog by remember { mutableStateOf<Long?>(null) }
    var showExportOptions by remember { mutableStateOf(false) }
    var showPdfList by remember { mutableStateOf(false) }
    var viewPdfFile by remember { mutableStateOf<File?>(null) }
    
    var capturedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showQuickCameraDialog by remember { mutableStateOf<android.net.Uri?>(null) }
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedImageUri?.let { uri ->
                showQuickCameraDialog = uri
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showReminderDialog = true
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val tempFile = File(context.cacheDir, "camera_temp.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
            capturedImageUri = uri
            cameraLauncher.launch(uri)
        }
    }

    customer?.let { currCustomer ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currCustomer.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, "tel:${currCustomer.phone}".toUri())
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.Call, contentDescription = "Call")
                            }
                            IconButton(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    showReminderDialog = true
                                }
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Set Reminder")
                            }
                            if (isSelectionMode) {
                                IconButton(onClick = { viewModel.toggleSelectionMode(false) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Exit Selection")
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showPdfList = true }) {
                            Icon(Icons.Default.DownloadForOffline, contentDescription = "View Exported PDFs")
                        }
                        IconButton(onClick = { showExportOptions = true }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                        }
                    }
                )
            },
            bottomBar = {
                if (isSelectionMode) {
                    BottomAppBar(
                        actions = {
                            Text("${selectedIds.size} Selected", modifier = Modifier.padding(start = 16.dp))
                        },
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        val details = viewModel.getTransactionsWithDetails(selectedIds.toList())
                                        PdfGenerator.generateCustomerLedger(context, currCustomer, details, PdfDetailLevel.DETAILED)
                                        viewModel.toggleSelectionMode(false)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Generate PDF")
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!isSelectionMode) {
                    Column(horizontalAlignment = Alignment.End) {
                        FloatingActionButton(
                            onClick = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Quick Bill (Camera)")
                        }

                        FloatingActionButton(
                            onClick = {
                                showAddTransactionDialog = TransactionType.DEBIT
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Add Bill")
                        }
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                // Reminder Banner
                currCustomer.reminderTime?.let { time ->
                    if (time > System.currentTimeMillis()) {
                        val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reminder: ${dateFormat.format(Date(time))}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.cancelReminder() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Reminder")
                            }
                        }
                    }
                }

                // Header with current balance
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currCustomer.totalBalance >= 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Current Balance", fontSize = 16.sp)
                        Text(
                            "₹${"%.2f".format(abs(currCustomer.totalBalance))}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currCustomer.totalBalance >= 0) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                        Text(
                            if (currCustomer.totalBalance >= 0) "You will receive" else "Advance Received",
                            fontSize = 14.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { 
                            showAddTransactionDialog = TransactionType.DEBIT 
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("YOU GAVE (DEBIT)")
                    }

                    Button(
                        onClick = { 
                            showAddTransactionDialog = TransactionType.CREDIT 
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("YOU GOT (CREDIT)")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (transactions.isEmpty()) {
                    EmptyStateGuidance(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = "No Transactions",
                        steps = listOf(
                            Icons.Default.ShoppingCart to "Tap 'YOU GAVE' to record sales.",
                            Icons.Default.Payments to "Tap 'YOU GOT' to record payments.",
                            Icons.Default.TouchApp to "Click any entry to view or edit details."
                        )
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(transactions) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                isSelected = selectedIds.contains(transaction.id),
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleTransactionSelection(transaction.id)
                                    } else {
                                        selectedTransactionIdForDetails = transaction.id
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.toggleSelectionMode(true)
                                        viewModel.toggleTransactionSelection(transaction.id)
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        if (showReminderDialog) {
            ReminderDialog(
                customerName = currCustomer.name,
                onDismiss = { showReminderDialog = false },
                onSchedule = { reminderTime ->
                    val delay = reminderTime - System.currentTimeMillis()
                    if (delay > 0) {
                        ReminderManager.scheduleReminder(context, currCustomer.name, delay)
                        viewModel.setReminder(reminderTime)
                    }
                    showReminderDialog = false
                }
            )
        }
    }

    showQuickCameraDialog?.let { uri ->
        AddTransactionDialog(
            viewModel = viewModel,
            type = TransactionType.DEBIT,
            initialAttachmentUri = uri,
            onDismiss = { showQuickCameraDialog = null },
            onAdd = { amount, note, timestamp, attachmentPath ->
                viewModel.addTransaction(
                    amount = amount, 
                    type = TransactionType.DEBIT, 
                    note = note, 
                    timestamp = timestamp,
                    attachmentPath = attachmentPath
                )
                showQuickCameraDialog = null
            }
        )
    }

    showAddTransactionDialog?.let { type ->
        AddTransactionDialog(
            viewModel = viewModel,
            type = type,
            onDismiss = { 
                showAddTransactionDialog = null
            },
            onAdd = { amount, note, timestamp, attachmentPath ->
                viewModel.addTransaction(
                    amount = amount, 
                    type = type, 
                    note = note, 
                    timestamp = timestamp,
                    attachmentPath = attachmentPath
                )
                showAddTransactionDialog = null
            }
        )
    }

    transactionToEdit?.let { transaction ->
        AddTransactionDialog(
            viewModel = viewModel,
            type = transaction.type,
            initialAmount = transaction.amount.toString(),
            initialNote = transaction.note,
            initialTimestamp = transaction.timestamp,
            initialAttachmentPath = transaction.attachmentPath,
            isEdit = true,
            onDismiss = { 
                transactionToEdit = null
            },
            onAdd = { amount, note, timestamp, attachmentPath ->
                viewModel.updateTransaction(
                    oldTransaction = transaction, 
                    newTransaction = transaction.copy(
                        amount = amount, 
                        note = note, 
                        timestamp = timestamp,
                        attachmentPath = attachmentPath
                    )
                )
                transactionToEdit = null
            }
        )
    }

    selectedTransactionIdForDetails?.let { id ->
        val linkedPayments by viewModel.selectedBillPayments.collectAsState()
        val transaction = transactions.find { it.id == id }
        
        LaunchedEffect(id) {
            viewModel.fetchBillItems(id)
        }

        BillDetailsDialog(
            transaction = transaction,
            linkedPayments = linkedPayments,
            onDismiss = {
                selectedTransactionIdForDetails = null
                viewModel.clearBillItems()
            },
            onEdit = {
                transactionToEdit = transaction
                selectedTransactionIdForDetails = null
            },
            onDelete = {
                transaction?.let { viewModel.deleteTransaction(it) }
                selectedTransactionIdForDetails = null
                viewModel.clearBillItems()
            },
            onRecordPayment = {
                showPartPaymentDialog = id
                selectedTransactionIdForDetails = null
            },
            onShare = {
                customer?.let { currCust ->
                    transaction?.let { currBill ->
                        ImageGenerator.shareBillImage(
                            context = context,
                            customer = currCust,
                            bill = currBill,
                            payments = linkedPayments,
                            attachmentPath = currBill.attachmentPath
                        )
                    }
                }
            },
            onImageClick = { path ->
                fullScreenImagePath = path
            }
        )
    }

    if (showExportOptions) {
        ExportOptionsDialog(
            onDismiss = { showExportOptions = false },
            onOptionSelected = { option, start, end ->
                showExportOptions = false
                
                val localStart = if (option == "Date") {
                    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = start }.let {
                        Calendar.getInstance().apply {
                            set(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DATE), 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                } else start

                val localEnd = if (option == "Date") {
                    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = end }.let {
                        Calendar.getInstance().apply {
                            set(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DATE), 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }.timeInMillis
                    }
                } else end

                scope.launch {
                    customer?.let { currCust ->
                        val details = when (option) {
                            "Full" -> viewModel.getAllTransactionsWithDetails()
                            "Medium" -> transactions.map { g.p.cbb.data.model.TransactionWithDetails(it) }
                            "Date" -> {
                                val filtered = transactions.filter { it.timestamp in localStart..localEnd }
                                viewModel.getTransactionsWithDetails(filtered.map { it.id })
                            }
                            else -> emptyList()
                        }
                        
                        if (option == "Selected") {
                            viewModel.toggleSelectionMode(true)
                        } else if (details.isNotEmpty()) {
                            val level = if (option == "Medium") PdfDetailLevel.SUMMARY else PdfDetailLevel.DETAILED
                            PdfGenerator.generateCustomerLedger(context, currCust, details, level)
                        }
                    }
                }
            }
        )
    }

    if (showPdfList) {
        val currentCustomer = customer
        if (currentCustomer != null) {
            CustomerPdfListDialog(
                customerName = currentCustomer.name,
                onDismiss = { showPdfList = false },
                onViewPdf = { file ->
                    viewPdfFile = file
                    showPdfList = false
                }
            )
        }
    }

    viewPdfFile?.let { file ->
        PdfViewer(
            file = file,
            onDismiss = { 
                viewPdfFile = null
                showPdfList = true 
            },
            onShare = {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Statement"))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Sharing failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                if (file.delete()) {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                    viewPdfFile = null
                    showPdfList = true 
                }
            }
        )
    }

    fullScreenImagePath?.let { path ->
        FullScreenImageViewer(
            imagePath = path,
            onDismiss = { fullScreenImagePath = null }
        )
    }

    showPartPaymentDialog?.let { billId ->
        PartPaymentDialog(
            onDismiss = { showPartPaymentDialog = null },
            onConfirm = { amount, note ->
                viewModel.addPartPayment(billId, amount, note)
                showPartPaymentDialog = null
            }
        )
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun TransactionItem(
    transaction: Transaction, 
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    ListItem(
        headlineContent = { 
            val headline = if (transaction.parentTransactionId != null) "Part Payment" 
            else transaction.note.ifEmpty { transaction.type.name }
            Column {
                Text(headline)
                if (transaction.createdBy != "admin") {
                    Text("By: ${transaction.createdBy}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        },
        supportingContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormat.format(Date(transaction.timestamp)))
                if (transaction.attachmentPath != null || transaction.driveFileId != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        },
        leadingContent = {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            }
        },
        trailingContent = {
            Text(
                "₹${"%.2f".format(transaction.amount)}",
                color = if (transaction.type == TransactionType.DEBIT) Color(0xFFF44336) else Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    viewModel: CbbViewModel,
    type: TransactionType,
    initialAmount: String = "",
    initialNote: String = "",
    initialTimestamp: Long = System.currentTimeMillis(),
    initialAttachmentPath: String? = null,
    initialAttachmentUri: android.net.Uri? = null,
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: (Double, String, Long, String?) -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(initialNote) }
    var selectedTimestamp by remember { mutableLongStateOf(initialTimestamp) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    var attachmentPath by remember { mutableStateOf(initialAttachmentPath) }
    var attachmentUri by remember { mutableStateOf(initialAttachmentUri) }
    var showReplacePhotoConfirm by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            attachmentUri = it
            attachmentPath = null
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedTimestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = datePickerState.selectedDateMillis ?: selectedTimestamp
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = selectedTimestamp
                        val h = get(Calendar.HOUR_OF_DAY)
                        val m = get(Calendar.MINUTE)
                        timeInMillis = date
                        set(Calendar.HOUR_OF_DAY, h)
                        set(Calendar.MINUTE, m)
                    }
                    selectedTimestamp = cal.timeInMillis
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }.get(Calendar.HOUR_OF_DAY),
            initialMinute = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = selectedTimestamp
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    selectedTimestamp = cal.timeInMillis
                    showTimePicker = false
                }) { Text("OK") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(if (isEdit) "Edit Entry" else (if (type == TransactionType.CREDIT) "Got Money" else "Gave Money"))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(dateFormat.format(Date(selectedTimestamp)), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp).clickable { showTimePicker = true })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(timeFormat.format(Date(selectedTimestamp)), fontSize = 12.sp, modifier = Modifier.clickable { showTimePicker = true })
                }
            }
        },
        text = {
            Column {
                TextField(
                    value = amount,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) amount = it },
                    label = { Text("Amount") },
                    leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = note, 
                    onValueChange = { note = it }, 
                    label = { Text("Note (Optional)") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { 
                            if (attachmentUri != null || attachmentPath != null) showReplacePhotoConfirm = true
                            else galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Attach Photo")
                    }
                    
                    if (attachmentUri != null || attachmentPath != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AsyncImage(
                            model = attachmentUri ?: attachmentPath,
                            contentDescription = "Preview",
                            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small).clickable {
                                showReplacePhotoConfirm = true
                            },
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                if (showReplacePhotoConfirm) {
                    AlertDialog(
                        onDismissRequest = { showReplacePhotoConfirm = false },
                        title = { Text("Replace Photo?") },
                        text = { Text("Do you want to replace or remove the current photo attachment?") },
                        confirmButton = {
                            Button(onClick = {
                                showReplacePhotoConfirm = false
                                galleryLauncher.launch("image/*")
                            }) { Text("Replace") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                attachmentUri = null
                                attachmentPath = null
                                showReplacePhotoConfirm = false
                            }) { Text("Remove", color = Color.Red) }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                val finalPath = attachmentUri?.let { ImageUtils.saveCompressedAttachment(context, it) } ?: attachmentPath
                onAdd(amount.toDoubleOrNull() ?: 0.0, note, selectedTimestamp, finalPath) 
            }) { Text(if (isEdit) "Update" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BillDetailsDialog(
    transaction: Transaction?, 
    linkedPayments: List<Transaction>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRecordPayment: () -> Unit,
    onShare: () -> Unit,
    onImageClick: (String) -> Unit
) {
    if (transaction == null) return
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bill Details", modifier = Modifier.weight(1f))
                if (transaction.type == TransactionType.DEBIT) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share Bill")
                    }
                }
            }
        },
        text = {
            Column {
                val imageSource = transaction.attachmentPath ?: transaction.driveFileId?.let { "https://drive.google.com/thumbnail?id=$it" }
                if (imageSource != null) {
                    AsyncImage(
                        model = imageSource,
                        contentDescription = "Attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { 
                                transaction.attachmentPath?.let { onImageClick(it) }
                                    ?: transaction.driveFileId?.let { onImageClick("https://drive.google.com/file/d/$it/view") }
                            }
                            .padding(bottom = 16.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                if (transaction.note.isEmpty()) {
                    Text("Lumpsum", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                } else {
                    Text("Note: ${transaction.note}", fontSize = 16.sp)
                }
                
                if (linkedPayments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Linked Payments:", fontWeight = FontWeight.Bold)
                    linkedPayments.forEach { pay ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dateFormat.format(Date(pay.timestamp)), modifier = Modifier.weight(1f))
                            Text("₹${"%.2f".format(pay.amount)}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        }
                        HorizontalDivider()
                    }
                }
                
                if (transaction.type == TransactionType.DEBIT) {
                    val totalBill = transaction.amount
                    val totalReceived = linkedPayments.sumOf { it.amount }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Total Bill: ₹${"%.2f".format(totalBill)}", fontSize = 14.sp)
                    Text("Total Received: ₹${"%.2f".format(totalReceived)}", fontSize = 14.sp, color = Color(0xFF4CAF50))
                    Text("Remaining: ₹${"%.2f".format(totalBill - totalReceived)}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFFF44336))
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onRecordPayment, modifier = Modifier.fillMaxWidth()) {
                        Text("Record Part Payment")
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                    Text("Delete")
                }
                Button(onClick = onEdit) { Text("Edit") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun PartPaymentDialog(onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Part Payment") },
        text = {
            Column {
                TextField(
                    value = amount,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) amount = it },
                    label = { Text("Amount Received") },
                    leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) }
                )
                TextField(
                    value = note, 
                    onValueChange = { note = it }, 
                    label = { Text("Note (Optional)") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0, note) }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(customerName: String, onDismiss: () -> Unit, onSchedule: (Long) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    
    val selectedDate = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDate))
    val timeStr = "${"%02d".format(timePickerState.hour)}:${"%02d".format(timePickerState.minute)}"

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("OK") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Reminder for $customerName") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("In 1 Day") },
                    modifier = Modifier.clickable { onSchedule(System.currentTimeMillis() + 24 * 60 * 60 * 1000L) }
                )
                ListItem(
                    headlineContent = { Text("In 1 Week") },
                    modifier = Modifier.clickable { onSchedule(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L) }
                )
                ListItem(
                    headlineContent = { Text("In 1 Month") },
                    modifier = Modifier.clickable { onSchedule(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000L) }
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Custom Date & Time", style = MaterialTheme.typography.labelMedium)
                ListItem(
                    headlineContent = { Text(dateStr) },
                    overlineContent = { Text("Date") },
                    modifier = Modifier.clickable { showDatePicker = true }
                )
                ListItem(
                    headlineContent = { Text(timeStr) },
                    overlineContent = { Text("Time") },
                    modifier = Modifier.clickable { showTimePicker = true }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = selectedDate
                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    set(Calendar.MINUTE, timePickerState.minute)
                }
                onSchedule(calendar.timeInMillis)
            }) { Text("Set Custom") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerPdfListDialog(
    customerName: String,
    onDismiss: () -> Unit,
    onViewPdf: (File) -> Unit
) {
    val context = LocalContext.current
    val pdfFiles = remember {
        val folder = StorageManager.getStatementFolder(context)
        folder.listFiles { file -> 
            file.name.startsWith("Statement_$customerName", ignoreCase = true) && file.name.endsWith(".pdf")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exported PDFs for $customerName") },
        text = {
            if (pdfFiles.isEmpty()) {
                Text("No exported PDFs found for this customer.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(pdfFiles) { file ->
                        ListItem(
                            headlineContent = { Text(file.name, style = MaterialTheme.typography.bodySmall) },
                            supportingContent = { 
                                val size = file.length() / 1024
                                Text("${dateFormat.format(Date(file.lastModified()))} • ${size}KB")
                            },
                            leadingContent = { Icon(Icons.Default.PictureAsPdf, null, tint = Color.Red) },
                            modifier = Modifier.clickable { onViewPdf(file) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportOptionsDialog(
    onDismiss: () -> Unit,
    onOptionSelected: (String, Long, Long) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    if (showDatePicker) {
        DateRangePickerDialog(
            state = dateRangePickerState,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                val start = dateRangePickerState.selectedStartDateMillis ?: 0L
                val end = dateRangePickerState.selectedEndDateMillis ?: System.currentTimeMillis()
                onOptionSelected("Date", start, end)
                showDatePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export PDF Options") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("Full Details") },
                    supportingContent = { Text("All bills with items and photos") },
                    leadingContent = { Icon(Icons.Default.Description, null) },
                    modifier = Modifier.clickable { onOptionSelected("Full", 0, 0) }
                )
                ListItem(
                    headlineContent = { Text("Medium (Summary)") },
                    supportingContent = { Text("Simple table of all transactions") },
                    leadingContent = { Icon(Icons.Default.TableChart, null) },
                    modifier = Modifier.clickable { onOptionSelected("Medium", 0, 0) }
                )
                ListItem(
                    headlineContent = { Text("Selected Bills") },
                    supportingContent = { Text("Pick specific bills from your ledger") },
                    leadingContent = { Icon(Icons.Default.LibraryAddCheck, null) },
                    modifier = Modifier.clickable { onOptionSelected("Selected", 0, 0) }
                )
                ListItem(
                    headlineContent = { Text("Date Range") },
                    supportingContent = { Text("Export detailed bills for specific dates") },
                    leadingContent = { Icon(Icons.Default.DateRange, null) },
                    modifier = Modifier.clickable { showDatePicker = true }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    state: DateRangePickerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select Dates") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                    },
                    actions = {
                        TextButton(onClick = onConfirm) { Text("Apply") }
                    }
                )
            }
        ) { padding ->
            DateRangePicker(state = state, modifier = Modifier.padding(padding))
        }
    }
}
