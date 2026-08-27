package g.p.cbb.utils

import g.p.cbb.data.entity.Transaction
import g.p.cbb.data.entity.TransactionType
import g.p.cbb.data.model.TransactionWithDetails
import kotlin.math.abs

object LedgerUtils {

    /**
     * Filters transactions to return only those starting from the most recent 0 balance (cleared balance) point.
     * Includes the transaction that cleared the balance to 0, followed by all subsequent entries.
     */
    fun getTransactionsSinceLastZeroBalance(transactions: List<TransactionWithDetails>): List<TransactionWithDetails> {
        if (transactions.isEmpty()) return transactions

        // Sort chronologically (oldest to newest) to compute running balance
        val chronological = transactions.sortedWith(compareBy({ it.transaction.timestamp }, { it.transaction.id }))

        var runningBalance = 0.0
        var lastZeroIndex = -1

        for (i in chronological.indices) {
            val tx = chronological[i].transaction
            if (tx.type == TransactionType.CREDIT) {
                runningBalance -= tx.amount
            } else {
                runningBalance += tx.amount
            }

            if (abs(runningBalance) < 0.001) {
                lastZeroIndex = i
            }
        }

        return if (lastZeroIndex != -1) {
            chronological.subList(lastZeroIndex, chronological.size)
        } else {
            chronological
        }
    }

    /**
     * Checks if a zero balance point exists in the transaction history.
     */
    fun hasZeroBalancePoint(transactions: List<TransactionWithDetails>): Boolean {
        if (transactions.isEmpty()) return false
        val chronological = transactions.sortedWith(compareBy({ it.transaction.timestamp }, { it.transaction.id }))
        var runningBalance = 0.0
        for (item in chronological) {
            val tx = item.transaction
            if (tx.type == TransactionType.CREDIT) {
                runningBalance -= tx.amount
            } else {
                runningBalance += tx.amount
            }
            if (abs(runningBalance) < 0.001) {
                return true
            }
        }
        return false
    }

    /**
     * Helper for raw List<Transaction>
     */
    fun getRawTransactionsSinceLastZeroBalance(transactions: List<Transaction>): List<Transaction> {
        if (transactions.isEmpty()) return transactions
        val chronological = transactions.sortedWith(compareBy({ it.timestamp }, { it.id }))
        var runningBalance = 0.0
        var lastZeroIndex = -1
        for (i in chronological.indices) {
            val tx = chronological[i]
            if (tx.type == TransactionType.CREDIT) {
                runningBalance -= tx.amount
            } else {
                runningBalance += tx.amount
            }
            if (abs(runningBalance) < 0.001) {
                lastZeroIndex = i
            }
        }
        return if (lastZeroIndex != -1) {
            chronological.subList(lastZeroIndex, chronological.size)
        } else {
            chronological
        }
    }
}
