package com.example.shiftsnap

import android.content.Context
import android.content.SharedPreferences

/**
 * Mengelola sesi login operator via SharedPreferences.
 * Key-key disimpan di satu tempat agar mudah di-clear saat logout.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Ambil waktu install aplikasi (selalu fresh setelah install ulang)
    private val installTime: Long = try {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .firstInstallTime
    } catch (e: Exception) {
        0L
    }

    companion object {
        private const val PREF_NAME      = "ShiftSnapSession"
        const val KEY_IS_LOGGED_IN       = "isLoggedIn"
        const val KEY_OPERATOR_ID        = "operatorId"
        const val KEY_DARK_MODE          = "darkMode"
        private const val KEY_LOGIN_TIME = "loginTime"

        // Data dummy operator (produksi: ganti dengan DB/API)
        val DUMMY_USERS = mapOf(
            "OPR001" to "shift123",
            "OPR002" to "pagi456",
            "SPV001" to "super789"
        )
    }

    fun saveLogin(operatorId: String) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_OPERATOR_ID, operatorId)
            .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            .apply()
    }

    /**
     * Cek apakah user masih login VALID.
     * Login dianggap tidak valid jika sesi dibuat SEBELUM install terakhir
     * (artinya SharedPreferences sisa dari install lama yang tidak ikut terhapus).
     */
    fun isLoggedIn(): Boolean {
        val loggedIn  = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val loginTime = prefs.getLong(KEY_LOGIN_TIME, 0L)

        if (!loggedIn) return false

        // Jika waktu login lebih lama dari waktu install → sesi basi, paksa logout
        if (installTime > 0L && loginTime < installTime) {
            logout()
            return false
        }

        return true
    }

    fun getOperatorId(): String = prefs.getString(KEY_OPERATOR_ID, "") ?: ""

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_IS_LOGGED_IN)
            .remove(KEY_OPERATOR_ID)
            .remove(KEY_LOGIN_TIME)
            .apply()
        // Dark-mode preference TIDAK dihapus saat logout agar tetap tersimpan
    }
}
