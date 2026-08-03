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

    fun getSpreadsheetId(): String? = prefs.getString("spreadsheet_id", "1tTnbqhjkKLSvQxm3rI-rHCue_oRhWIjgzgZQsySuR58")
    fun saveSpreadsheetId(id: String) = prefs.edit().putString("spreadsheet_id", id.trim()).apply()

    fun getDriveFolderId(): String? = prefs.getString("drive_folder_id", null)
    fun saveDriveFolderId(id: String) = prefs.edit().putString("drive_folder_id", id).apply()

    fun getUserEmail(): String? = prefs.getString("user_email", null)
    fun saveUserEmail(email: String?) = prefs.edit().putString("user_email", email).apply()

    fun getUserName(): String? = prefs.getString("user_name", null)
    fun saveUserName(name: String?) = prefs.edit().putString("user_name", name).apply()

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun saveAccessToken(token: String?) = prefs.edit().putString("access_token", token).apply()

    fun getLastViewedTransactionsTime(): Long = prefs.getLong("last_viewed_transactions_time", 0L)
    fun saveLastViewedTransactionsTime(time: Long) = prefs.edit().putLong("last_viewed_transactions_time", time).apply()

    fun getNotificationsEnabled(): Boolean = prefs.getBoolean("notifications_enabled", true)
    fun saveNotificationsEnabled(enabled: Boolean) = prefs.edit().putBoolean("notifications_enabled", enabled).apply()

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.SYSTEM }
    }
    fun saveThemeMode(mode: ThemeMode) = prefs.edit().putString("theme_mode", mode.name).apply()
}

enum class SortOption {
    NAME,
    BALANCE_LOW_TO_HIGH,
    BALANCE_HIGH_TO_LOW
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
