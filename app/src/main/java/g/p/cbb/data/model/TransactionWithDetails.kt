package g.p.cbb.data.model

import g.p.cbb.data.entity.BillItem
import g.p.cbb.data.entity.Transaction

data class TransactionWithDetails(
    val transaction: Transaction,
    val billItems: List<BillItem> = emptyList()
)
