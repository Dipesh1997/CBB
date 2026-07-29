package g.p.cbb.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import g.p.cbb.data.entity.BillItem
import g.p.cbb.data.entity.ProductSuggestion
import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import g.p.cbb.ui.components.EmptyStateGuidance
import g.p.cbb.utils.*
import g.p.cbb.viewmodel.CbbViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(viewModel: CbbViewModel, onBack: () -> Unit) {
    val customer by viewModel.selectedCustomer.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    var showAddTransactionDialog by remember { mutableStateOf<TransactionType?>(null) }
    var startInBillMode by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var selectedTransactionIdForDetails by remember { mutableStateOf<Long?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showPartPaymentDialog by remember { mutableStateOf<Long?>(null) }
    
    var capturedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showQuickCameraDialog by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = LocalContext.current

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
        ActivityResultContracts.RequestPermission()
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
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            PdfGenerator.generateCustomerLedger(context, currCustomer, transactions)
                        }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                        }
                    }
                )
            },
            floatingActionButton = {
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
                            startInBillMode = true
                            showAddTransactionDialog = TransactionType.DEBIT
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Create Bill")
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
                            startInBillMode = false
                            showAddTransactionDialog = TransactionType.DEBIT 
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("YOU GAVE (DEBIT)")
                    }

                    Button(
                        onClick = { 
                            startInBillMode = false
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
                            TransactionItem(transaction, onClick = {
                                selectedTransactionIdForDetails = transaction.id
                                viewModel.fetchBillItems(transaction.id)
                            })
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
            onAdd = { amount, note, _, timestamp, attachmentPath ->
                viewModel.addTransaction(
                    amount = amount, 
                    type = TransactionType.DEBIT, 
                    note = note, 
                    billItems = emptyList(), 
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
            startInBillMode = startInBillMode,
            onDismiss = { 
                showAddTransactionDialog = null
                startInBillMode = false
            },
            onAdd = { amount, note, billItems, timestamp, attachmentPath ->
                viewModel.addTransaction(
                    amount = amount, 
                    type = type, 
                    note = note, 
                    billItems = billItems, 
                    timestamp = timestamp,
                    attachmentPath = attachmentPath
                )
                showAddTransactionDialog = null
                startInBillMode = false
            }
        )
    }

    transactionToEdit?.let { transaction ->
        val billItems by viewModel.selectedTransactionItems.collectAsState()
        AddTransactionDialog(
            viewModel = viewModel,
            type = transaction.type,
            initialAmount = transaction.amount.toString(),
            initialNote = transaction.note,
            initialTimestamp = transaction.timestamp,
            initialBillItems = billItems.map { it.productName to it.price.toString() },
            initialAttachmentPath = transaction.attachmentPath,
            isEdit = true,
            onDismiss = { 
                transactionToEdit = null
                viewModel.clearBillItems()
            },
            onAdd = { amount, note, items, timestamp, attachmentPath ->
                viewModel.updateTransaction(
                    oldTransaction = transaction, 
                    newTransaction = transaction.copy(
                        amount = amount, 
                        note = note, 
                        timestamp = timestamp,
                        attachmentPath = attachmentPath
                    ), 
                    billItems = items
                )
                transactionToEdit = null
                viewModel.clearBillItems()
            }
        )
    }

    selectedTransactionIdForDetails?.let { id ->
        val billItems by viewModel.selectedTransactionItems.collectAsState()
        val linkedPayments by viewModel.selectedBillPayments.collectAsState()
        val transaction = transactions.find { it.id == id }
        BillDetailsDialog(
            billItems = billItems,
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
                            items = billItems,
                            payments = linkedPayments,
                            attachmentPath = currBill.attachmentPath
                        )
                    }
                }
            }
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

@Composable
fun TransactionItem(transaction: Transaction, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    ListItem(
        headlineContent = { 
            val headline = if (transaction.parentTransactionId != null) "Part Payment" 
            else transaction.note.ifEmpty { transaction.type.name }
            Text(headline)
        },
        supportingContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormat.format(Date(transaction.timestamp)))
                if (transaction.attachmentPath != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
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
        modifier = Modifier.clickable { onClick() }
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
    initialBillItems: List<Pair<String, String>> = emptyList(),
    initialAttachmentPath: String? = null,
    initialAttachmentUri: android.net.Uri? = null,
    startInBillMode: Boolean = false,
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: (Double, String, List<BillItem>, Long, String?) -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(initialNote) }
    var isBillMode by remember { mutableStateOf(initialBillItems.isNotEmpty() || startInBillMode) }
    
    var selectedTimestamp by remember { mutableLongStateOf(initialTimestamp) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    var attachmentPath by remember { mutableStateOf(initialAttachmentPath) }
    var attachmentUri by remember { mutableStateOf(initialAttachmentUri) }
    
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            attachmentUri = it
            attachmentPath = null // Reset path to indicate new URI needs processing
        }
    }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedTimestamp)
    val timePickerState = rememberTimePickerState(
        initialHour = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }.get(Calendar.HOUR_OF_DAY),
        initialMinute = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }.get(Calendar.MINUTE)
    )

    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    val billItems = remember { 
        val list = mutableStateListOf<Pair<String, String>>()
        list.addAll(initialBillItems)
        if (list.isEmpty()) list.add("" to "")
        list 
    }

    val suggestions by viewModel.productSuggestions.collectAsState(initial = emptyList())

    var isListening by remember { mutableStateOf(false) }
    val voiceRecognizer = remember {
        lateinit var recognizer: VoiceRecognizer
        recognizer = VoiceRecognizer(
            context = context,
            onResult = { text ->
                val normalizedText = TextNormalizer.normalize(text)
                val segments = normalizedText.split("(?i)\\b(next|done)\\b".toRegex())
                val commands = "(?i)\\b(next|done)\\b".toRegex().findAll(normalizedText).map { it.value.lowercase() }.toList()
                
                segments.forEachIndexed { index, segment ->
                    val productPhrase = segment.trim()
                    if (productPhrase.isNotEmpty()) {
                        val lastMatch = "(?i)\\blast\\s+(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)\\b".toRegex().find(productPhrase)
                        if (lastMatch != null) {
                            val qtyText = lastMatch.groupValues[1]
                            val qty = if (qtyText.any { it.isDigit() }) qtyText.toInt() else {
                                when(qtyText.lowercase()) {
                                    "one" -> 1; "two" -> 2; "three" -> 3; "four" -> 4; "five" -> 5
                                    "six" -> 6; "seven" -> 7; "eight" -> 8; "nine" -> 9; "ten" -> 10
                                    else -> 1
                                }
                            }
                            val lastIndex = billItems.size - 1
                            if (lastIndex >= 0) {
                                val currentItemName = billItems[lastIndex].first
                                val currentItemPrice = billItems[lastIndex].second.toDoubleOrNull() ?: 0.0
                                val suggestion = suggestions.find { it.name.equals(currentItemName, ignoreCase = true) }
                                val (newName, newPrice) = ProductParser.applyQuantity(
                                    currentName = currentItemName,
                                    currentPrice = currentItemPrice,
                                    newQuantity = qty,
                                    units = suggestion?.units,
                                    basePrice = suggestion?.lastPrice ?: currentItemPrice
                                )
                                billItems[lastIndex] = newName to newPrice.toString()
                            }
                        } else {
                            val matched = suggestions.find { it.shortcut.equals(productPhrase, ignoreCase = true) }
                                ?: suggestions.find { it.name.equals(productPhrase, ignoreCase = true) }
                            matched?.let { m ->
                                val lastIndex = billItems.size - 1
                                if (lastIndex >= 0) {
                                    billItems[lastIndex] = m.name to m.lastPrice.toString()
                                }
                            }
                        }
                    }
                    if (index < commands.size) {
                        val cmd = commands[index]
                        if (cmd == "next") {
                            if (billItems.last().first.isNotEmpty()) billItems.add("" to "")
                        } else if (cmd == "done") {
                            recognizer.stopListening()
                            val items = billItems.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
                                .map { BillItem(productName = it.first, price = it.second.toDoubleOrNull() ?: 0.0, transactionId = 0) }
                            val total = items.sumOf { it.price }
                            onAdd(total, "Bill: ${items.size} items", items, selectedTimestamp, null)
                        }
                    }
                }
            },
            onStateChange = { isListening = it }
        )
        recognizer
    }

    DisposableEffect(Unit) {
        onDispose { voiceRecognizer.destroy() }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) voiceRecognizer.startListening()
    }

    if (showDatePicker) {
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

    if (isBillMode) {
        val totalBill = billItems.sumOf { it.second.toDoubleOrNull() ?: 0.0 }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column {
                    Text(if (isEdit) "Edit Bill" else "Create Bill")
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(billItems.size) { index ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1.5f).padding(end = 4.dp)) {
                                    TextField(
                                        value = billItems[index].first,
                                        onValueChange = { billItems[index] = it to billItems[index].second },
                                        label = { Text("Product") },
                                        leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    val filteredSuggestions = suggestions.filter { 
                                        it.name.contains(billItems[index].first, ignoreCase = true) && 
                                        it.name != billItems[index].first
                                    }
                                    if (filteredSuggestions.isNotEmpty() && billItems[index].first.isNotEmpty()) {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            filteredSuggestions.take(3).forEach { suggestion ->
                                                DropdownMenuItem(
                                                    text = { Text("${suggestion.name} (₹${suggestion.lastPrice})") },
                                                    onClick = { 
                                                        billItems[index] = suggestion.name to suggestion.lastPrice.toString()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                TextField(
                                    value = billItems[index].second,
                                    onValueChange = { billItems[index] = billItems[index].first to it },
                                    label = { Text("Price") },
                                    leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { billItems.add("" to "") }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("New Line")
                        }
                        IconButton(
                            onClick = {
                                if (isListening) voiceRecognizer.stopListening()
                                else voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                                contentDescription = "Voice Typing",
                                tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total Bill: ₹${"%.2f".format(totalBill)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val items = billItems.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
                        .map { BillItem(productName = it.first, price = it.second.toDoubleOrNull() ?: 0.0, transactionId = 0) }
                    onAdd(totalBill, "Bill: ${items.size} items", items, selectedTimestamp, null)
                }) { Text("Finish Bill") }
            },
            dismissButton = {
                TextButton(onClick = { if (startInBillMode) onDismiss() else isBillMode = false }) { Text("Back") }
            }
        )
    } else {
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
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) }
                    )
                    TextField(
                        value = note, 
                        onValueChange = { note = it }, 
                        label = { Text("Note (Optional)") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Photo Attachment Area
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
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
                                    attachmentUri = null
                                    attachmentPath = null
                                },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    if (type == TransactionType.DEBIT && !isEdit) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { 
                            isBillMode = true
                            if (billItems.isEmpty()) billItems.add("" to "")
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Create Itemized Bill Instead")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { 
                    val finalPath = attachmentUri?.let { ImageUtils.saveCompressedAttachment(context, it) } ?: attachmentPath
                    onAdd(amount.toDoubleOrNull() ?: 0.0, note, emptyList(), selectedTimestamp, finalPath) 
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun BillDetailsDialog(
    billItems: List<BillItem>, 
    transaction: Transaction?, 
    linkedPayments: List<Transaction>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRecordPayment: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bill Details", modifier = Modifier.weight(1f))
                if (transaction?.type == TransactionType.DEBIT) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share Bill")
                    }
                }
            }
        },
        text = {
            Column {
                if (transaction?.attachmentPath != null) {
                    AsyncImage(
                        model = transaction.attachmentPath,
                        contentDescription = "Attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .padding(bottom = 16.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                if (billItems.isEmpty()) {
                    if (transaction?.note.isNullOrEmpty()) {
                        Text("Lumpsum", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    } else {
                        Text("Note: ${transaction?.note}", fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(billItems) { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.productName, modifier = Modifier.weight(1f))
                                Text("₹${"%.2f".format(item.price)}", fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider()
                        }
                    }
                    val totalBill = billItems.sumOf { it.price }
                    val totalReceived = linkedPayments.sumOf { it.amount }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Total Bill: ₹${"%.2f".format(totalBill)}", fontSize = 14.sp)
                    Text("Total Received: ₹${"%.2f".format(totalReceived)}", fontSize = 14.sp, color = Color(0xFF4CAF50))
                    Text("Remaining: ₹${"%.2f".format(totalBill - totalReceived)}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFFF44336))
                }
                
                if (transaction?.type == TransactionType.DEBIT) {
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
        ) { DatePicker(state = datePickerState) }
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
