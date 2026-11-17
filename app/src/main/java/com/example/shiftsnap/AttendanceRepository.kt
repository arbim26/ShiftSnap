package com.example.shiftsnap.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Penyimpanan absensi di SharedPreferences (JSON) supaya tetap ada
 * walaupun aplikasi ditutup. Produksi: ganti dengan Room / API.
 */
class AttendanceRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val jam = SimpleDateFormat("HH:mm", Locale("id", "ID"))

    // ── Baca ────────────────────────────────────────────────
    fun all(): List<AttendanceRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun today(operatorId: String): List<AttendanceRecord> =
        all().filter { it.operatorId == operatorId && isToday(it.timestamp) }

    /**
     * Absen masuk untuk sesi kerja yang sedang berjalan: masuk hari ini, ATAU masuk
     * kemarin yang belum ada pulangnya (Shift 3 malam melewati tengah malam).
     * Sesi terbuka dibatasi [MAKS_SESI_MS] supaya lupa absen pulang tidak mengunci selamanya.
     */
    fun todayMasuk(operatorId: String): AttendanceRecord? {
        val mine = all().filter { it.operatorId == operatorId }
        val masuk = mine.lastOrNull { it.jenis == AttendanceRecord.MASUK } ?: return null
        if (isToday(masuk.timestamp)) return masuk
        val sudahPulang = mine.any {
            it.jenis == AttendanceRecord.PULANG && it.timestamp > masuk.timestamp
        }
        val masihBerjalan = System.currentTimeMillis() - masuk.timestamp < MAKS_SESI_MS
        return if (!sudahPulang && masihBerjalan) masuk else null
    }

    /** Absen pulang untuk sesi di atas (pulang setelah absen masuk tersebut). */
    fun todayPulang(operatorId: String): AttendanceRecord? {
        val masuk = todayMasuk(operatorId) ?: return null
        return all().lastOrNull {
            it.operatorId == operatorId &&
                    it.jenis == AttendanceRecord.PULANG &&
                    it.timestamp > masuk.timestamp
        }
    }

    /** Shift yang dipilih operator saat absen masuk hari ini (null kalau belum absen masuk). */
    fun todayShift(operatorId: String): String? = todayMasuk(operatorId)?.shift

    /** Ringkasan status hari ini, mis. "Masuk 08:12 · Pulang 17:05". */
    fun statusText(operatorId: String): String {
        val masuk = todayMasuk(operatorId)
        val pulang = todayPulang(operatorId)
        return when {
            masuk == null -> "Belum absen"
            pulang == null -> "Masuk ${jam.format(Date(masuk.timestamp))}"
            else -> "Masuk ${jam.format(Date(masuk.timestamp))} · Pulang ${jam.format(Date(pulang.timestamp))}"
        }
    }

    // ── Tulis ───────────────────────────────────────────────
    fun add(record: AttendanceRecord) {
        val arr = JSONArray()
        all().forEach { arr.put(toJson(it)) }
        arr.put(toJson(record))
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    // ── Util ────────────────────────────────────────────────
    private fun isToday(millis: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = millis }
        val b = Calendar.getInstance()
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun toJson(r: AttendanceRecord) = JSONObject().apply {
        put("id", r.id)
        put("operatorId", r.operatorId)
        put("jenis", r.jenis)
        put("timestamp", r.timestamp)
        put("latitude", r.latitude)
        put("longitude", r.longitude)
        put("fotoLokasiUri", r.fotoLokasiUri)
        put("fotoWajahUri", r.fotoWajahUri)
        put("laporanKerja", r.laporanKerja ?: JSONObject.NULL)
        put("fotoKerjaUri", r.fotoKerjaUri ?: JSONObject.NULL)
        put("shift", r.shift ?: JSONObject.NULL)
    }

    private fun fromJson(o: JSONObject) = AttendanceRecord(
        id = o.getString("id"),
        operatorId = o.getString("operatorId"),
        jenis = o.getString("jenis"),
        timestamp = o.getLong("timestamp"),
        latitude = o.getDouble("latitude"),
        longitude = o.getDouble("longitude"),
        fotoLokasiUri = o.getString("fotoLokasiUri"),
        fotoWajahUri = o.getString("fotoWajahUri"),
        laporanKerja = if (o.isNull("laporanKerja")) null else o.getString("laporanKerja"),
        fotoKerjaUri = if (o.isNull("fotoKerjaUri")) null else o.getString("fotoKerjaUri"),
        // data lama (sebelum ada shift) tidak punya key ini → optString/has aman
        shift = if (!o.has("shift") || o.isNull("shift")) null else o.getString("shift")
    )

    companion object {
        private const val PREF_NAME = "ShiftSnapAttendance"
        private const val KEY_RECORDS = "records"
        private const val MAKS_SESI_MS = 16L * 60 * 60 * 1000   // 16 jam
    }
}