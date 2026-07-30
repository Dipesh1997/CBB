package g.p.cbb.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("cbb_settings", Context.MODE_PRIVATE)

    fun getSortOption(): SortOption {
        val name = prefs.getString("sort_option", SortOption.NAME.name) ?: SortOption.NAME.name
        return try {
            SortOption.valueOf(name)
        } catch (e: Exception) {
            SortOption.NAME
        }
    }

    fun saveSortOption(option: SortOption) {
        prefs.edit().putString("sort_option", option.name).apply()
    }

    fun getSpreadsheetId(): String? = prefs.getString("spreadsheet_id", null)
    fun saveSpreadsheetId(id: String) = prefs.edit().putString("spreadsheet_id", id).apply()

    fun getDriveFolderId(): String? = prefs.getString("drive_folder_id", null)
    fun saveDriveFolderId(id: String) = prefs.edit().putString("drive_folder_id", id).apply()
}

enum class SortOption {
    NAME,
    BALANCE_LOW_TO_HIGH,
    BALANCE_HIGH_TO_LOW
}
