package g.p.cbb.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import g.p.cbb.data.entity.ProductSuggestion
import g.p.cbb.ui.components.EmptyStateGuidance
import g.p.cbb.viewmodel.CbbViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(viewModel: CbbViewModel) {
    val products by viewModel.productSuggestions.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductSuggestion?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Product Catalog") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (products.isEmpty()) {
                EmptyStateGuidance(
                    icon = Icons.Default.Inventory2,
                    title = "Your Catalog is Empty",
                    steps = listOf(
                        Icons.Default.Add to "Manually add common items here.",
                        Icons.Default.Sell to "Or record bills to 'learn' new items automatically.",
                        Icons.Default.Payments to "Manage standard prices for quick billing."
                    )
                )
            } else {
                LazyColumn {
                    items(products) { product ->
                        ListItem(
                            headlineContent = { Text(product.name, fontWeight = FontWeight.SemiBold) },
                            trailingContent = { 
                                Text("₹${"%.2f".format(product.lastPrice)}", style = MaterialTheme.typography.titleMedium) 
                            },
                            modifier = Modifier.clickable { productToEdit = product }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ProductDialog(
            title = "Add Product",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, price ->
                viewModel.addProduct(name, price)
                showAddDialog = false
            }
        )
    }

    productToEdit?.let { product ->
        ProductDialog(
            title = "Edit Product",
            initialName = product.name,
            initialPrice = product.lastPrice.toString(),
            onDismiss = { productToEdit = null },
            onConfirm = { name, price ->
                viewModel.updateProduct(ProductSuggestion(name, price))
                productToEdit = null
            },
            showDelete = true,
            onDelete = {
                viewModel.deleteProduct(product)
                productToEdit = null
            }
        )
    }
}

@Composable
fun ProductDialog(
    title: String,
    initialName: String = "",
    initialPrice: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit,
    showDelete: Boolean = false,
    onDelete: () -> Unit = {}
) {
    var name by remember { mutableStateOf(initialName) }
    var price by remember { mutableStateOf(initialPrice) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    leadingIcon = { Icon(Icons.Default.Inventory2, null) }
                )
                TextField(
                    value = price,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) price = it },
                    label = { Text("Standard Price") },
                    leadingIcon = { Icon(Icons.Default.Payments, null) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, price.toDoubleOrNull() ?: 0.0) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (showDelete) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
