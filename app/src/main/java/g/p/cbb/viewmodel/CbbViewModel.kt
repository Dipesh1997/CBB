package g.p.cbb.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import g.p.cbb.data.entity.*
import g.p.cbb.repository.CbbRepository
import g.p.cbb.repository.SettingsRepository
import g.p.cbb.repository.SortOption
import g.p.cbb.utils.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CbbViewModel @Inject constructor(
    private val repository: CbbRepository,
    private val settingsRepository: SettingsRepository,
    private val authManager: GoogleAuthManager,
    private val syncManager: g.p.cbb.utils.CloudSyncManager
) : ViewModel() {

    sealed class SyncEvent {
        data class RequestAuthorization(val intent: android.content.Intent) : SyncEvent()
        data class Error(val message: String) : SyncEvent()
        object Success : SyncEvent()
        object PickAccount : SyncEvent()
    }

    private val _syncEvents = MutableSharedFlow<SyncEvent>()
    val syncEvents = _syncEvents.asSharedFlow()

    val userEmail = authManager.userEmail
    val userName = authManager.userName

    private val _availableAccounts = MutableStateFlow<List<String>>(emptyList())
    val availableAccounts = _availableAccounts.asStateFlow()

    private val _showAccountPickerDialog = MutableStateFlow(false)
    val showAccountPickerDialog = _showAccountPickerDialog.asStateFlow()

    fun openAccountPicker(context: android.content.Context) {
        val am = android.accounts.AccountManager.get(context)
        val googleAccounts = try {
            am.getAccountsByType("com.google").map { it.name }
        } catch (e: Exception) {
            emptyList()
        }
        _availableAccounts.value = googleAccounts
        _showAccountPickerDialog.value = true
    }

    fun dismissAccountPicker() {
        _showAccountPickerDialog.value = false
    }

    fun selectAccount(email: String) {
        _showAccountPickerDialog.value = false
        authManager.forceAccountLink(email)
        syncNow()
    }

    fun saveOAuthToken(token: String) {
        viewModelScope.launch {
            authManager.saveOAuthAccessToken(token)
            performSync(isManual = true)
        }
    }

    fun signIn(context: android.content.Context) {
        viewModelScope.launch {
            val success = authManager.signIn(context)
            if (success) {
                repository.markAllDataAsUnsynced()
                performSync(isManual = true)
            }
        }
    }

    fun syncNow() {
        performSync(isManual = true)
    }

    fun pickAccount() {
        viewModelScope.launch {
            _syncEvents.emit(SyncEvent.PickAccount)
        }
    }

    private fun performSync(isManual: Boolean) {
        viewModelScope.launch {
            try {
                syncManager.fullSync()
                if (isManual) _syncEvents.emit(SyncEvent.Success)
            } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                e.intent?.let { _syncEvents.emit(SyncEvent.RequestAuthorization(it)) }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                e.intent?.let { _syncEvents.emit(SyncEvent.RequestAuthorization(it)) }
            } catch (e: Exception) {
                val cause = e.cause
                val intent = when {
                    e is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException -> e.intent
                    cause is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException -> cause.intent
                    cause is com.google.android.gms.auth.UserRecoverableAuthException -> cause.intent
                    else -> null
                }
                if (intent != null) {
                    _syncEvents.emit(SyncEvent.RequestAuthorization(intent))
                } else if (isManual) {
                    val detailMsg = when {
                        e is com.google.api.client.googleapis.json.GoogleJsonResponseException -> {
                            "Google API Error (${e.statusCode}): ${e.details?.message ?: e.message}"
                        }
                        cause is com.google.api.client.googleapis.json.GoogleJsonResponseException -> {
                            "Google API Error (${cause.statusCode}): ${cause.details?.message ?: cause.message}"
                        }
                        else -> e.message ?: cause?.message ?: e.javaClass.simpleName
                    }
                    if (detailMsg.contains("UnregisteredOnApiConsole", ignoreCase = true)) {
                        _syncEvents.emit(SyncEvent.Error("Google Console Registration Required:\nAdd Android Client ID for package g.p.cbb and SHA-1 ED:33:39:09:21:C0:08:08:DE:38:86:D6:41:25:90:A1:ED:CB:88:E8 in Google Cloud Console Credentials."))
                    } else if (detailMsg.contains("name must not be empty", ignoreCase = true)) {
                        _syncEvents.emit(SyncEvent.PickAccount)
                    } else {
                        _syncEvents.emit(SyncEvent.Error(detailMsg))
                    }
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
        }
    }

    fun inviteCollaborator(email: String) {
        viewModelScope.launch {
            syncManager.inviteCollaborator(email)
        }
    }

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
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activityLogs = repository.getActivityLogs()
    val unreadHistoryCount = repository.getUnreadLogCount()

    fun markHistoryAsRead() {
        viewModelScope.launch {
            repository.markLogsAsRead()
        }
    }

    private val _backupHistory = MutableStateFlow<List<java.io.File>>(emptyList())
    val backupHistory = _backupHistory.asStateFlow()

    fun refreshBackupHistory(context: android.content.Context) {
        _backupHistory.value = g.p.cbb.utils.BackupManager.getBackupHistory(context)
    }

    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer = _selectedCustomer.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _selectedBillPayments = MutableStateFlow<List<Transaction>>(emptyList())
    val selectedBillPayments = _selectedBillPayments.asStateFlow()

    // Multi-Selection State
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _selectedTransactionIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTransactionIds = _selectedTransactionIds.asStateFlow()

    fun toggleSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) _selectedTransactionIds.value = emptySet()
    }

    fun toggleTransactionSelection(id: Long) {
        val current = _selectedTransactionIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedTransactionIds.value = current
    }

    fun getTransactionsWithDetails(ids: List<Long>): List<g.p.cbb.data.model.TransactionWithDetails> {
        val list = mutableListOf<g.p.cbb.data.model.TransactionWithDetails>()
        ids.forEach { id ->
            val transaction = transactions.value.find { it.id == id }
            if (transaction != null) {
                list.add(g.p.cbb.data.model.TransactionWithDetails(transaction))
            }
        }
        return list.sortedByDescending { it.transaction.timestamp }
    }

    fun getAllTransactionsWithDetails(): List<g.p.cbb.data.model.TransactionWithDetails> {
        val list = mutableListOf<g.p.cbb.data.model.TransactionWithDetails>()
        transactions.value.forEach { transaction ->
            list.add(g.p.cbb.data.model.TransactionWithDetails(transaction))
        }
        return list.sortedByDescending { it.transaction.timestamp }
    }

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
            performSync(isManual = false)
        }
    }

    fun updateCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
            if (_selectedCustomer.value?.id == customer.id) {
                _selectedCustomer.value = customer
            }
            performSync(isManual = false)
        }
    }

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
            performSync(isManual = false)
        }
    }

    fun addTransaction(
        amount: Double, 
        type: TransactionType, 
        note: String, 
        timestamp: Long = System.currentTimeMillis(),
        attachmentPath: String? = null
    ) {
        val customer = _selectedCustomer.value ?: return
        viewModelScope.launch {
            repository.addTransaction(
                Transaction(
                    customerId = customer.id,
                    amount = amount,
                    type = type,
                    note = note,
                    timestamp = timestamp,
                    attachmentPath = attachmentPath
                ),
                timestamp
            )
            refreshCustomer(customer.id)
            performSync(isManual = false)
        }
    }

    fun updateTransaction(oldTransaction: Transaction, newTransaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(oldTransaction, newTransaction)
            refreshCustomer(newTransaction.customerId)
            performSync(isManual = false)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            refreshCustomer(transaction.customerId)
            performSync(isManual = false)
        }
    }

    private suspend fun refreshCustomer(customerId: Long) {
        _selectedCustomer.value = repository.getCustomerById(customerId)
    }

    fun fetchBillItems(transactionId: Long) {
        viewModelScope.launch {
            _selectedBillPayments.value = repository.getLinkedTransactions(transactionId)
        }
    }

    fun clearBillItems() {
        _selectedBillPayments.value = emptyList()
    }

    fun updateSortOption(SortOption: SortOption) {
        _sortOption.value = SortOption
        settingsRepository.saveSortOption(SortOption)
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
            performSync(isManual = false)
        }
    }

    fun setReminder(reminderTime: Long) {
        val customer = _selectedCustomer.value ?: return
        viewModelScope.launch {
            repository.updateCustomerReminder(customer.id, reminderTime)
            refreshCustomer(customer.id)
            performSync(isManual = false)
        }
    }

    fun cancelReminder() {
        val customer = _selectedCustomer.value ?: return
        viewModelScope.launch {
            repository.updateCustomerReminder(customer.id, null)
            refreshCustomer(customer.id)
            performSync(isManual = false)
        }
    }
}
