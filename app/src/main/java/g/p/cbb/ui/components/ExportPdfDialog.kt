package g.p.cbb.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.data.entity.Customer
import g.p.cbb.data.model.TransactionWithDetails
import g.p.cbb.utils.PdfDetailLevel
import g.p.cbb.utils.PdfGenerator
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExportPdfDialog(
    customer: Customer,
    transactions: List<TransactionWithDetails>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    var endDateMillis by remember { mutableStateOf<Long?>(null) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    fun showDatePicker(initialMillis: Long?, onSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        if (initialMillis != null) cal.timeInMillis = initialMillis
        val dpd = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onSelected(selectedCal.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dpd.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Export Customer Statement",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Full Detailed Button
                Button(
                    onClick = {
                        PdfGenerator.generateCustomerLedger(
                            context = context,
                            customer = customer,
                            transactions = transactions,
                            detailLevel = PdfDetailLevel.DETAILED
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Full Statement (Detailed with Photos)")
                }

                // Summary Button
                OutlinedButton(
                    onClick = {
                        PdfGenerator.generateCustomerLedger(
                            context = context,
                            customer = customer,
                            transactions = transactions,
                            detailLevel = PdfDetailLevel.SUMMARY
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.TableRows, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Summary Statement (Table View)")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Custom Date Range Section
                Text(
                    text = "Custom Date Range Export:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showDatePicker(startDateMillis) { start ->
                                startDateMillis = start
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (startDateMillis != null) dateFormatter.format(Date(startDateMillis!!)) else "Start Date",
                            fontSize = 11.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            showDatePicker(endDateMillis) { end ->
                                // Set end date to 23:59:59
                                val endCal = Calendar.getInstance().apply {
                                    timeInMillis = end
                                    set(Calendar.HOUR_OF_DAY, 23)
                                    set(Calendar.MINUTE, 59)
                                    set(Calendar.SECOND, 59)
                                }
                                endDateMillis = endCal.timeInMillis
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (endDateMillis != null) dateFormatter.format(Date(endDateMillis!!)) else "End Date",
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        if (startDateMillis != null && endDateMillis != null) {
                            PdfGenerator.generateCustomerLedger(
                                context = context,
                                customer = customer,
                                transactions = transactions,
                                detailLevel = PdfDetailLevel.DETAILED,
                                startDate = startDateMillis,
                                endDate = endDateMillis
                            )
                            onDismiss()
                        }
                    },
                    enabled = startDateMillis != null && endDateMillis != null && startDateMillis!! <= endDateMillis!!,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Export for Selected Dates")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
