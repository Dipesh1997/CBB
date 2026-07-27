package g.p.cbb.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import g.p.cbb.data.entity.*
import g.p.cbb.repository.CbbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CbbViewModel @Inject constructor(
    private val repository: CbbRepository
) : ViewModel() {

    val customers = repository.getAllCustomers()
    val activityLogs = repository.getActivityLogs()

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

    fun addTransaction(amount: Double, type: TransactionType, note: String, billItems: List<BillItem> = emptyList()) {
        val customer = _selectedCustomer.value ?: return
        viewModelScope.launch {
            repository.addTransaction(
                Transaction(
                    customerId = customer.id,
                    amount = amount,
                    type = type,
                    note = note
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
