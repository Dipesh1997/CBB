package g.p.cbb.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import g.p.cbb.data.entity.*
import g.p.cbb.repository.CbbRepository
import g.p.cbb.repository.SettingsRepository
import g.p.cbb.repository.SortOption
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CbbViewModel @Inject constructor(
    private val repository: CbbRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _sortOption = MutableStateFlow(settingsRepository.getSortOption())
    val sortOption = _sortOption.asStateFlow()

    val customers = combine(repository.getAllCustomers(), _sortOption) { customers, option ->
        val active = customers.filter { !it.isBadDebt }.let { list ->
            when (option) {
                SortOption.NAME -> list.sortedBy { it.name }
                SortOption.BALANCE_LOW_TO_HIGH -> list.sortedBy { it.totalBalance }
                SortOption.BALANCE_HIGH_TO_LOW -> list.sortedByDescending { it.totalBalance }
            }
        }
        val badDebt = customers.filter { it.isBadDebt }.sortedBy { it.name }
        active + badDebt
    }

    val activityLogs = repository.getActivityLogs()
    val productSuggestions = repository.getProductSuggestions()

    private val _backupHistory = MutableStateFlow<List<java.io.File>>(emptyList())
    val backupHistory = _backupHistory.asStateFlow()

    fun refreshBackupHistory(context: android.content.Context) {
        _backupHistory.value = g.p.cbb.utils.BackupManager.getBackupHistory(context)
    }

    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer = _selectedCustomer.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _selectedTransactionItems = MutableStateFlow<List<BillItem>>(emptyList())
    val selectedTransactionItems = _selectedTransactionItems.asStateFlow()

    private val _selectedBillPayments = MutableStateFlow<List<Transaction>>(emptyList())
    val selectedBillPayments = _selectedBillPayments.asStateFlow()

    fun selectCustomer(customer: Customer) {
        _selectedCustomer.value = customer
        viewModelScope.launch {
            repository.getTransactionsForCustomer(customer.id).collect {
                _transactions.value = it
            }
        }
    }

    fun addCustomer(name: String, phone: String, address: String) {
        viewModelScope.launch {
            repository.addCustomer(Customer(name = name, phone = phone, address = address))
        }
    }

    fun updateCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
            if (_selectedCustomer.value?.id == customer.id) {
                _selectedCustomer.value = customer
            }
        }
    }

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
        }
    }

    fun addTransaction(amount: Double, type: TransactionType, note: String, billItems: List<BillItem> = emptyList(), timestamp: Long = System.currentTimeMillis()) {
        val customer = _selectedCustomer.value ?: return
        viewModelScope.launch {
            repository.addTransaction(
                Transaction(
                    customerId = customer.id,
                    amount = amount,
                    type = type,
                    note = note,
                    timestamp = timestamp
                ),
                billItems
            )
            refreshCustomer(customer.id)
        }
    }

    fun updateTransaction(oldTransaction: Transaction, newTransaction: Transaction, billItems: List<BillItem> = emptyList()) {
        viewModelScope.launch {
            repository.updateTransaction(oldTransaction, newTransaction, billItems)
            refreshCustomer(newTransaction.customerId)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            refreshCustomer(transaction.customerId)
        }
    }

    private suspend fun refreshCustomer(customerId: Long) {
        _selectedCustomer.value = repository.getCustomerById(customerId)
    }

    fun fetchBillItems(transactionId: Long) {
        viewModelScope.launch {
            _selectedTransactionItems.value = repository.getBillItemsForTransaction(transactionId)
            _selectedBillPayments.value = repository.getLinkedTransactions(transactionId)
        }
    }

    fun clearBillItems() {
        _selectedTransactionItems.value = emptyList()
        _selectedBillPayments.value = emptyList()
    }

    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
        settingsRepository.saveSortOption(option)
    }

    fun addProduct(name: String, price: Double) {
        viewModelScope.launch {
            repository.addProductSuggestion(name, price)
        }
    }

    fun updateProduct(suggestion: ProductSuggestion) {
        viewModelScope.launch {
            repository.updateProductSuggestion(suggestion)
        }
    }

    fun deleteProduct(suggestion: ProductSuggestion) {
        viewModelScope.launch {
            repository.deleteProductSuggestion(suggestion)
        }
    }

    fun restoreLatest(context: android.content.Context) {
        viewModelScope.launch {
            val error = repository.restoreLatestBackup(context)
            if (error == null) {
                // Try to refresh. Usually app needs restart but we try.
                _selectedCustomer.value?.let { refreshCustomer(it.id) }
                refreshBackupHistory(context)
            }
        }
    }

    fun restoreSpecific(context: android.content.Context, file: java.io.File) {
        viewModelScope.launch {
            val error = g.p.cbb.utils.BackupManager.importSpecificDatabase(context, repository.getDatabase(), file)
            if (error == null) {
                _selectedCustomer.value?.let { refreshCustomer(it.id) }
                refreshBackupHistory(context)
            }
        }
    }

    fun addPartPayment(billId: Long, amount: Double, note: String) {
        val customer = _selectedCustomer.value ?: return
        viewModelScope.launch {
            repository.addTransaction(
                Transaction(
                    customerId = customer.id,
                    amount = amount,
                    type = TransactionType.CREDIT,
                    note = note,
                    parentTransactionId = billId
                )
            )
            refreshCustomer(customer.id)
            _selectedBillPayments.value = repository.getLinkedTransactions(billId)
        }
    }

    fun setReminder(reminderTime: Long) {
        val customer = _selectedCustomer.value ?: return
        viewModelScope.launch {
            repository.updateCustomerReminder(customer.id, reminderTime)
            refreshCustomer(customer.id)
        }
    }

    fun cancelReminder() {
        val customer = _selectedCustomer.value ?: return
        viewModelScope.launch {
            repository.updateCustomerReminder(customer.id, null)
            refreshCustomer(customer.id)
        }
    }
}
