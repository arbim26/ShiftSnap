package com.example.shiftsnap.model

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository untuk mengelola data absensi operator.
 * Menggunakan SharedPreferences & in-memory list untuk persistensi data.
 */
class AttendanceRepository(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.applicationContext?.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE,
    )
    private val gson = Gson()

    init {
        if (recordsInMemory.isEmpty() && (prefs != null)) {
            loadFromPrefs()
        }
    }

    /** Tambah catatan absensi baru. */
    fun add(record: AttendanceRecord) {
        recordsInMemory.add(0, record)
        saveToPrefs()
    }

    /** Ambil seluruh daftar absensi. */
    fun getAll(): List<AttendanceRecord> = recordsInMemory.toList()

    /** Ambil absen masuk hari ini untuk operator tertentu. */
    fun todayMasuk(operatorId: String): AttendanceRecord? {
        return recordsInMemory.firstOrNull {
            it.operatorId == operatorId &&
                    (it.jenis == AttendanceRecord.MASUK) &&
                    isToday(it.timestamp)
        }
    }

    /** Ambil absen pulang hari ini untuk operator tertentu. */
    fun todayPulang(operatorId: String): AttendanceRecord? {
        return recordsInMemory.firstOrNull {
            it.operatorId == operatorId &&
                    (it.jenis == AttendanceRecord.PULANG) &&
                    isToday(it.timestamp)
        }
    }

    /** Mendapatkan teks status absensi hari ini. */
    fun statusText(operatorId: String): String {
        val masuk = todayMasuk(operatorId)
        val pulang = todayPulang(operatorId)
        return when {
            pulang != null -> "Sudah Absen Pulang"
            masuk != null -> "Sudah Absen Masuk"
            else -> "Belum Absen"
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return fmt.format(Date(timestamp)) == fmt.format(Date())
    }

    private fun saveToPrefs() {
        prefs?.let { p ->
            val json = gson.toJson(recordsInMemory)
            p.edit().putString(KEY_RECORDS, json).apply()
        }
    }

    private fun loadFromPrefs() {
        prefs?.let {
            val json = it.getString(KEY_RECORDS, null)
            if (!json.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<MutableList<AttendanceRecord>>() {}.type
                    val loaded: MutableList<AttendanceRecord>? = gson.fromJson(json, type)
                    if (loaded != null) {
                        recordsInMemory.clear()
                        recordsInMemory.addAll(loaded)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        private const val PREF_NAME = "ShiftSnapAttendance"
        private const val KEY_RECORDS = "attendance_records"

        // In-memory backing store shared across repository instances
        private val recordsInMemory = mutableListOf<AttendanceRecord>()
    }
}
