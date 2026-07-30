package g.p.cbb.utils

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*

object GoogleSheetsHelper {

    private const val CUSTOMERS_SHEET = "Customers"
    private const val TRANSACTIONS_SHEET = "Transactions"
    private const val CATALOG_SHEET = "Catalog"
    private const val HISTORY_SHEET = "History"
    private const val TRASH_SHEET = "Trash"
    private const val BILL_ITEMS_SHEET = "BillItems"

    fun setupSheets(sheets: Sheets, spreadsheetId: String) {
        val spreadsheet = sheets.spreadsheets().get(spreadsheetId).execute()
        val existingTitles = spreadsheet.sheets.map { it.properties.title }

        val requests = mutableListOf<Request>()
        if (!existingTitles.contains(CUSTOMERS_SHEET)) requests.add(addSheetRequest(CUSTOMERS_SHEET))
        if (!existingTitles.contains(TRANSACTIONS_SHEET)) requests.add(addSheetRequest(TRANSACTIONS_SHEET))
        if (!existingTitles.contains(CATALOG_SHEET)) requests.add(addSheetRequest(CATALOG_SHEET))
        if (!existingTitles.contains(HISTORY_SHEET)) requests.add(addSheetRequest(HISTORY_SHEET))
        if (!existingTitles.contains(TRASH_SHEET)) requests.add(addSheetRequest(TRASH_SHEET))
        if (!existingTitles.contains(BILL_ITEMS_SHEET)) requests.add(addSheetRequest(BILL_ITEMS_SHEET))

        if (requests.isNotEmpty()) {
            sheets.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(requests)).execute()
            // Add Headers
            writeHeaders(sheets, spreadsheetId)
        }
    }

    private fun addSheetRequest(title: String) = Request().setAddSheet(
        AddSheetRequest().setProperties(SheetProperties().setTitle(title))
    )

    private fun writeHeaders(sheets: Sheets, spreadsheetId: String) {
        val customersHeader = listOf("ID", "Name", "Phone", "Address", "Balance", "IsBadDebt", "CreatedBy", "LastUpdated", "ServerID")
        val transactionsHeader = listOf("ID", "CustomerServerID", "Amount", "Type", "Timestamp", "Note", "Attachment", "CreatedBy", "LastUpdated", "ServerID")
        val catalogHeader = listOf("ID", "Name", "Price", "Shortcut", "Units", "CreatedBy", "LastUpdated", "ServerID")
        val historyHeader = listOf("ID", "Timestamp", "Action", "IsCloud", "ServerID")
        val trashHeader = listOf("Summary", "Type", "OriginalServerID", "DeletedAt", "DataBackup")
        val billItemsHeader = listOf("ID", "TransactionServerID", "ProductName", "Price", "LastUpdated", "ServerID")
        
        updateRange(sheets, spreadsheetId, "$CUSTOMERS_SHEET!A1", listOf(customersHeader))
        updateRange(sheets, spreadsheetId, "$TRANSACTIONS_SHEET!A1", listOf(transactionsHeader))
        updateRange(sheets, spreadsheetId, "$CATALOG_SHEET!A1", listOf(catalogHeader))
        updateRange(sheets, spreadsheetId, "$HISTORY_SHEET!A1", listOf(historyHeader))
        updateRange(sheets, spreadsheetId, "$TRASH_SHEET!A1", listOf(trashHeader))
        updateRange(sheets, spreadsheetId, "$BILL_ITEMS_SHEET!A1", listOf(billItemsHeader))
    }

    fun updateRange(sheets: Sheets, spreadsheetId: String, range: String, values: List<List<Any>>) {
        val body = ValueRange().setValues(values)
        sheets.spreadsheets().values()
            .update(spreadsheetId, range, body)
            .setValueInputOption("RAW")
            .execute()
    }
}
