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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import g.p.cbb.data.entity.BillItem
import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import g.p.cbb.ui.components.EmptyStateGuidance
import g.p.cbb.utils.ImageGenerator
import g.p.cbb.utils.PdfGenerator
import g.p.cbb.utils.ReminderManager
import g.p.cbb.viewmodel.CbbViewModel
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
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showReminderDialog = true
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

    showAddTransactionDialog?.let { type ->
        AddTransactionDialog(
            viewModel = viewModel,
            type = type,
            startInBillMode = startInBillMode,
            onDismiss = { 
                showAddTransactionDialog = null
                startInBillMode = false
            },
            onAdd = { amount, note, billItems, timestamp ->
                viewModel.addTransaction(amount, type, note, billItems, timestamp)
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
            isEdit = true,
            onDismiss = { 
                transactionToEdit = null
                viewModel.clearBillItems()
            },
            onAdd = { amount, note, items, timestamp ->
                viewModel.updateTransaction(transaction, transaction.copy(amount = amount, note = note, timestamp = timestamp), items)
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
                            payments = linkedPayments
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
        supportingContent = { Text(dateFormat.format(Date(transaction.timestamp))) },
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
    startInBillMode: Boolean = false,
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: (Double, String, List<BillItem>, Long) -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(initialNote) }
    var isBillMode by remember { mutableStateOf(initialBillItems.isNotEmpty() || startInBillMode) }
    
    var selectedTimestamp by remember { mutableLongStateOf(initialTimestamp) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
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
                    TextButton(onClick = { billItems.add("" to "") }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("New Line")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total Bill: ₹${"%.2f".format(totalBill)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val items = billItems.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
                        .map { BillItem(productName = it.first, price = it.second.toDoubleOrNull() ?: 0.0, transactionId = 0) }
                    onAdd(totalBill, "Bill: ${items.size} items", items, selectedTimestamp)
                }) { Text("Finish Bill") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (startInBillMode) onDismiss() else isBillMode = false 
                }) { Text("Back") }
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
                    if (type == TransactionType.DEBIT) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { 
                            isBillMode = true
                            if (billItems.isEmpty()) billItems.add("" to "")
                        }) {
                            Text("Create Bill Instead")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onAdd(amount.toDoubleOrNull() ?: 0.0, note, emptyList(), selectedTimestamp) }) { Text("Save") }
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
                if (transaction?.type == TransactionType.DEBIT && billItems.isNotEmpty()) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share Bill")
                    }
                }
            }
        },
        text = {
            Column {
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
