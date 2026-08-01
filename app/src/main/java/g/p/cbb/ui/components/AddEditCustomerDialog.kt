package g.p.cbb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.data.entity.Customer
import g.p.cbb.ui.theme.Info

@Composable
fun AddEditCustomerDialog(
    customerToEdit: Customer? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, address: String, isBadDebt: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(customerToEdit?.name ?: "") }
    var phone by remember { mutableStateOf(customerToEdit?.phone ?: "") }
    var address by remember { mutableStateOf(customerToEdit?.address ?: "") }
    var isBadDebt by remember { mutableStateOf(customerToEdit?.isBadDebt ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (customerToEdit == null) "Add New Customer" else "Edit Customer",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Info,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Full name of the customer for PDF statements",
                        fontSize = 11.sp,
                        color = Info
                    )
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Info,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Phone number for sharing bill details",
                        fontSize = 11.sp,
                        color = Info
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = isBadDebt,
                        onCheckedChange = { isBadDebt = it }
                    )
                    Text(
                        text = "Flag as Bad Debt / Default Risk",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onConfirm(name, phone, address, isBadDebt)
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(if (customerToEdit == null) "Save Customer" else "Update Customer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
