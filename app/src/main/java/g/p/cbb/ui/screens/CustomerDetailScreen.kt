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
    
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                                showReminderDialog = true
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Set Reminder")
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
                            Icon(Icons.Default.DownloadForOffline, contentDescription = "View PDFs")
                        }
                        IconButton(onClick = { showExportOptions = true }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!isSelectionMode) {
                    FloatingActionButton(
                        onClick = { showAddTransactionDialog = TransactionType.DEBIT },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Bill")
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                // Profile Card (Web Parity)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PHONE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(currCustomer.phone, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ADDRESS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(currCustomer.address.ifEmpty { "N/A" }, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val bal = currCustomer.totalBalance
                            Text("BALANCE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                "₹${"%.2f".format(abs(bal))}", 
                                fontSize = 24.sp, 
                                fontWeight = FontWeight.ExtraBold,
                                color = if (bal >= 0) Color(0xFFF44336) else Color(0xFF4CAF50)
                            )
                            Text(if (bal >= 0) "Receivable" else "Advance", fontSize = 10.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showAddTransactionDialog = TransactionType.DEBIT },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) { Text("YOU GAVE") }

                    Button(
                        onClick = { showAddTransactionDialog = TransactionType.CREDIT },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("YOU GOT") }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (transactions.isEmpty()) {
                    EmptyStateGuidance(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = "Empty Ledger",
                        steps = listOf(
                            Icons.Default.Add to "Tap '+' to record your first transaction.",
                            Icons.Default.Payments to "Record part payments easily.",
                            Icons.Default.TouchApp to "Click any row for full details."
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
                                    if (isSelectionMode) viewModel.toggleTransactionSelection(transaction.id)
                                    else selectedTransactionIdForDetails = transaction.id
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.toggleSelectionMode(true)
                                        viewModel.toggleTransactionSelection(transaction.id)
                                    }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
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
                    ReminderManager.scheduleReminder(context, currCustomer.name, reminderTime - System.currentTimeMillis())
                    viewModel.setReminder(reminderTime)
                    showReminderDialog = false
                }
            )
        }
    }

    showAddTransactionDialog?.let { type ->
        AddTransactionDialog(
            viewModel = viewModel,
            type = type,
            onDismiss = { showAddTransactionDialog = null },
            onAdd = { amount, note, timestamp, attachmentPath ->
                viewModel.addTransaction(amount, type, note, timestamp, attachmentPath)
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
            onDismiss = { transactionToEdit = null },
            onAdd = { amount, note, timestamp, attachmentPath ->
                viewModel.updateTransaction(transaction, transaction.copy(amount = amount, note = note, timestamp = timestamp, attachmentPath = attachmentPath))
                transactionToEdit = null
            }
        )
    }

    selectedTransactionIdForDetails?.let { id ->
        val linkedPayments by viewModel.selectedBillPayments.collectAsState()
        val transaction = transactions.find { it.id == id }
        
        LaunchedEffect(id) { viewModel.fetchBillItems(id) }

        BillDetailsDialog(
            transaction = transaction,
            linkedPayments = linkedPayments,
            onDismiss = { selectedTransactionIdForDetails = null; viewModel.clearBillItems() },
            onEdit = { transactionToEdit = transaction; selectedTransactionIdForDetails = null },
            onDelete = { transaction?.let { viewModel.deleteTransaction(it) }; selectedTransactionIdForDetails = null },
            onRecordPayment = { showPartPaymentDialog = id; selectedTransactionIdForDetails = null },
            onShare = {
                customer?.let { currCust ->
                    transaction?.let { currBill ->
                        ImageGenerator.shareBillImage(context, currCust, currBill, linkedPayments, currBill.attachmentPath)
                    }
                }
            },
            onImageClick = { path -> fullScreenImagePath = path }
        )
    }

    if (showExportOptions) {
        ExportOptionsDialog(
            onDismiss = { showExportOptions = false },
            onOptionSelected = { option, start, end ->
                showExportOptions = false
                scope.launch {
                    customer?.let { currCust ->
                        val details = when (option) {
                            "Full" -> viewModel.getAllTransactionsWithDetails()
                            "Medium" -> transactions.map { g.p.cbb.data.model.TransactionWithDetails(it) }
                            "Date" -> {
                                val filtered = transactions.filter { it.timestamp in start..end }
                                viewModel.getTransactionsWithDetails(filtered.map { it.id })
                            }
                            else -> emptyList()
                        }
                        if (option == "Selected") viewModel.toggleSelectionMode(true)
                        else if (details.isNotEmpty()) PdfGenerator.generateCustomerLedger(context, currCust, details, if (option == "Medium") PdfDetailLevel.SUMMARY else PdfDetailLevel.DETAILED)
                    }
                }
            }
        )
    }

    if (showPdfList) {
        customer?.let { currCust ->
            CustomerPdfListDialog(
                customerName = currCust.name,
                onDismiss = { showPdfList = false },
                onViewPdf = { file -> viewPdfFile = file; showPdfList = false }
            )
        }
    }

    viewPdfFile?.let { file ->
        PdfViewer(
            file = file,
            onDismiss = { viewPdfFile = null; showPdfList = true },
            onShare = {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                context.startActivity(Intent.createChooser(intent, "Share Statement"))
            },
            onDelete = { if (file.delete()) { viewPdfFile = null; showPdfList = true } }
        )
    }

    fullScreenImagePath?.let { path ->
        FullScreenImageViewer(imagePath = path, onDismiss = { fullScreenImagePath = null })
    }

    showPartPaymentDialog?.let { billId ->
        PartPaymentDialog(
            onDismiss = { showPartPaymentDialog = null },
            onConfirm = { amount, note -> viewModel.addPartPayment(billId, amount, note); showPartPaymentDialog = null }
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
            Text(if (transaction.parentServerId != null) "Part Payment" else transaction.note.ifEmpty { transaction.type.name }, fontWeight = FontWeight.Medium)
        },
        supportingContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormat.format(Date(transaction.timestamp)), fontSize = 12.sp, color = Color.Gray)
                if (transaction.attachmentPath != null || transaction.driveFileId != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                }
            }
        },
        leadingContent = {
            if (isSelectionMode) Checkbox(checked = isSelected, onCheckedChange = { _ -> onClick() })
            else {
                val icon = if (transaction.type == TransactionType.DEBIT) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                val tint = if (transaction.type == TransactionType.DEBIT) Color(0xFFF44336) else Color(0xFF4CAF50)
                Box(modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(tint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                }
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
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: (Double, String, Long, String?) -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(initialNote) }
    var timestamp by remember { mutableLongStateOf(initialTimestamp) }
    var attachmentPath by remember { mutableStateOf(initialAttachmentPath) }

    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    var showDatePicker by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { attachmentPath = ImageUtils.saveCompressedAttachment(context, it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Entry" else "New ${if (type == TransactionType.DEBIT) "Debit" else "Credit"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amount = it },
                    label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                
                OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(dateFormat.format(Date(timestamp)))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Attach Photo")
                    }
                    attachmentPath?.let { path ->
                        Spacer(modifier = Modifier.width(8.dp))
                        AsyncImage(model = path, null, modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small).clickable { attachmentPath = null }, contentScale = ContentScale.Crop)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(amount.toDoubleOrNull() ?: 0.0, note, timestamp, attachmentPath) }) {
                Text(if (isEdit) "Update" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { timestamp = it }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = datePickerState) }
    }
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
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bill Details", modifier = Modifier.weight(1f))
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share") }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val imageSource = transaction.attachmentPath ?: transaction.driveFileId?.let { "https://drive.google.com/thumbnail?id=$it&sz=w1000" }
                if (imageSource != null) {
                    AsyncImage(
                        model = imageSource, null, 
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium).clickable { onImageClick(imageSource) },
                        contentScale = ContentScale.Fit
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Amount:", fontWeight = FontWeight.Bold)
                    Text("₹${"%.2f".format(transaction.amount)}", color = if (transaction.type == TransactionType.DEBIT) Color.Red else Color.Green, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Date:", fontWeight = FontWeight.Bold)
                    Text(dateFormat.format(Date(transaction.timestamp)))
                }
                if (transaction.note.isNotEmpty()) {
                    Column {
                        Text("Note:", fontWeight = FontWeight.Bold)
                        Text(transaction.note)
                    }
                }

                if (linkedPayments.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Linked Payments:", fontWeight = FontWeight.Bold)
                    linkedPayments.forEach { pay ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dateFormat.format(Date(pay.timestamp)), style = MaterialTheme.typography.bodySmall)
                            Text("₹${"%.2f".format(pay.amount)}", color = Color.Green, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (transaction.type == TransactionType.DEBIT) {
                    val received = linkedPayments.sumOf { it.amount }
                    val remaining = transaction.amount - received
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Remaining: ₹${"%.2f".format(remaining)}", fontWeight = FontWeight.ExtraBold, color = Color.Red, fontSize = 18.sp)
                        }
                    }
                    Button(onClick = onRecordPayment, modifier = Modifier.fillMaxWidth()) { Text("Record Part Payment") }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text("Delete", color = Color.Red) }
                Button(onClick = onEdit) { Text("Edit") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0, note) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
                Text("No exported PDFs found.")
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
                    supportingContent = { Text("Statement with Notes and Photos") },
                    leadingContent = { Icon(Icons.Default.Description, null) },
                    modifier = Modifier.clickable { onOptionSelected("Full", 0, 0) }
                )
                ListItem(
                    headlineContent = { Text("Medium (Summary)") },
                    supportingContent = { Text("Simplified transaction table") },
                    leadingContent = { Icon(Icons.Default.TableChart, null) },
                    modifier = Modifier.clickable { onOptionSelected("Medium", 0, 0) }
                )
                ListItem(
                    headlineContent = { Text("Date Range") },
                    supportingContent = { Text("Export for specific dates") },
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
