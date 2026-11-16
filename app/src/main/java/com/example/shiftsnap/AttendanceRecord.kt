package com.example.shiftsnap.model

import java.util.UUID

/**
 * Satu catatan absensi (masuk ATAU pulang).
 * Absen pulang wajib mengisi [laporanKerja] (pekerjaan yang dikerjakan hari itu).
 */
data class AttendanceRecord(
    val id: String = UUID.randomUUID().toString(),
    val operatorId: String,
    val jenis: String,                       // MASUK / PULANG
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val fotoLokasiUri: String,
    val fotoWajahUri: String,
    val laporanKerja: String? = null,        // hanya untuk PULANG
    val fotoKerjaUri: String? = null,        // opsional, hanya untuk PULANG
    val shift: String? = null                // dipilih saat MASUK (lihat SHIFT_OPTIONS)
) {
    companion object {
        const val MASUK = "MASUK"
        const val PULANG = "PULANG"

        // Satu sumber daftar shift, dipakai layar absen dan form laporan.
        val SHIFT_OPTIONS = listOf(
            "Shift 1 – Pagi  (06.00–14.00)",
            "Shift 2 – Siang (14.00–22.00)",
            "Shift 3 – Malam (22.00–06.00)"
        )
    }
}