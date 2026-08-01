package g.p.cbb.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import g.p.cbb.data.entity.Customer
import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import g.p.cbb.ui.theme.Error
import g.p.cbb.ui.theme.Info
import g.p.cbb.ui.theme.Warning
import g.p.cbb.ui.theme.WarningContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionDialog(
    customers: List<Customer>,
    preselectedCustomerId: Long? = null,
    transactionToEdit: Transaction? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        customerId: Long,
        amount: Double,
        type: TransactionType,
        note: String,
        photoUri: Uri?
    ) -> Unit
) {
    var selectedCustomer by remember {
        mutableStateOf(
            transactionToEdit?.let { tx -> customers.find { it.id == tx.customerId } }
                ?: preselectedCustomerId?.let { id -> customers.find { it.id == id } }
                ?: customers.firstOrNull()
        )
    }

    var amountText by remember { mutableStateOf(transactionToEdit?.amount?.toString() ?: "") }
    var type by remember { mutableStateOf(transactionToEdit?.type ?: TransactionType.DEBIT) }
    var note by remember { mutableStateOf(transactionToEdit?.note ?: "") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> photoUri = uri }

    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (transactionToEdit == null) "Add New Transaction" else "Edit Existing Bill",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bad Debt Alert Banner if selected customer is Bad Debt
                if (selectedCustomer?.isBadDebt == true) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEE2E2), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFDC2626).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Caution: Selected customer is marked as Bad Debt!",
                                fontSize = 11.sp,
                                color = Color(0xFF991B1B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Customer Selection Dropdown
                if (preselectedCustomerId == null && transactionToEdit == null) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCustomer?.name ?: "Select Customer",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Customer") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            customers.forEach { customer ->
                                DropdownMenuItem(
                                    text = { Text(customer.name) },
                                    onClick = {
                                        selectedCustomer = customer
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Amount Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Validation Warnings
                if (parsedAmount >= 50000) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Warning: High transaction amount (₹${parsedAmount.toInt()}). Double-check digits.",
                            fontSize = 11.sp,
                            color = Warning,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (type == TransactionType.CREDIT && selectedCustomer != null && selectedCustomer!!.totalBalance > 0 && parsedAmount > selectedCustomer!!.totalBalance) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Info, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Notice: Payment exceeds pending balance. Customer will have advance credit.",
                            fontSize = 11.sp,
                            color = Info
                        )
                    }
                }

                // Transaction Type Toggle Button Group
                Text(
                    text = "Transaction Type",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == TransactionType.DEBIT,
                        onClick = { type = TransactionType.DEBIT },
                        label = { Text("YOU GAVE (Debit)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFEBEE),
                            selectedLabelColor = Color(0xFFB71C1C)
                        )
                    )

                    FilterChip(
                        selected = type == TransactionType.CREDIT,
                        onClick = { type = TransactionType.CREDIT },
                        label = { Text("YOU GOT (Credit)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE8F5E9),
                            selectedLabelColor = Color(0xFF1B5E20)
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Info, modifier = Modifier.size(14.dp))
                    Text(
                        text = if (type == TransactionType.DEBIT) "DEBIT increases customer debt balance." else "CREDIT records payment received.",
                        fontSize = 11.sp,
                        color = Info
                    )
                }

                // Note Field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Items Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Photo Attachment Area
                OutlinedButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (photoUri == null && transactionToEdit?.driveFileId == null) "Attach Receipt Photo" else "Change Receipt Photo")
                }

                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Selected Receipt",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cust = selectedCustomer
                    if (cust != null && parsedAmount > 0) {
                        onConfirm(cust.id, parsedAmount, type, note, photoUri)
                    }
                },
                enabled = selectedCustomer != null && parsedAmount > 0,
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(if (transactionToEdit == null) "Save Transaction" else "Update Bill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
