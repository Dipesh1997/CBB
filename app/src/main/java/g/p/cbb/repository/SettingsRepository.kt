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
        val name = prefs.getString("sort_option", SortOption.NAME_ASC.name)
        return SortOption.fromString(name)
    }

    fun saveSortOption(option: SortOption) {
        prefs.edit().putString("sort_option", option.name).apply()
    }

    fun getSpreadsheetId(): String? {
        val email = getUserEmail()
        if (!email.isNullOrEmpty()) {
            val userSpecificId = prefs.getString("spreadsheet_id_$email", null)
            if (userSpecificId != null) return userSpecificId
        }
        return prefs.getString("spreadsheet_id", null)
    }

    fun saveSpreadsheetId(id: String) {
        val cleanId = id.trim()
        prefs.edit().putString("spreadsheet_id", cleanId).apply()
        val email = getUserEmail()
        if (!email.isNullOrEmpty()) {
            prefs.edit().putString("spreadsheet_id_$email", cleanId).apply()
        }
    }

    fun clearSpreadsheetIdForUser(email: String?) {
        if (!email.isNullOrEmpty()) {
            prefs.edit().remove("spreadsheet_id_$email").apply()
        }
        prefs.edit().remove("spreadsheet_id").apply()
    }

    fun getDriveFolderId(): String? = prefs.getString("drive_folder_id", null)
    fun saveDriveFolderId(id: String) = prefs.edit().putString("drive_folder_id", id).apply()

    fun getUserEmail(): String? = prefs.getString("user_email", null)
    fun saveUserEmail(email: String?) = prefs.edit().putString("user_email", email).apply()

    fun getUserName(): String? = prefs.getString("user_name", null)
    fun saveUserName(name: String?) = prefs.edit().putString("user_name", name).apply()

    fun getUserProfilePic(): String? = prefs.getString("user_profile_pic", null)
    fun saveUserProfilePic(url: String?) = prefs.edit().putString("user_profile_pic", url).apply()

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

    fun getCompressProfilePhotos(): Boolean = prefs.getBoolean("compress_profile_photos", true)
    fun saveCompressProfilePhotos(enabled: Boolean) = prefs.edit().putBoolean("compress_profile_photos", enabled).apply()
}

enum class SortOption(val label: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_NEWEST("Date (Newest First)"),
    DATE_OLDEST("Date (Oldest First)"),
    AMOUNT_HIGH_TO_LOW("Amount (High to Low)"),
    AMOUNT_LOW_TO_HIGH("Amount (Low to High)");

    companion object {
        fun fromString(value: String?): SortOption {
            return when (value) {
                "NAME" -> NAME_ASC
                "BALANCE_HIGH_TO_LOW" -> AMOUNT_HIGH_TO_LOW
                "BALANCE_LOW_TO_HIGH" -> AMOUNT_LOW_TO_HIGH
                else -> entries.firstOrNull { it.name == value } ?: NAME_ASC
            }
        }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
