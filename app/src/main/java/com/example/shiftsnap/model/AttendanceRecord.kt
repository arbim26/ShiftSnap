package com.example.shiftsnap.model

import java.util.UUID

/** Satu entri absensi (masuk atau pulang). */
data class AttendanceRecord(
    val id: String = UUID.randomUUID().toString(),
    val operatorId: String,
    val jenis: String,              // "MASUK" / "PULANG"
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val fotoLokasiUri: String,
    val fotoWajahUri: String,
    val laporanKerja: String? = null,   // diisi saat absen PULANG
    val fotoKerjaUri: String? = null    // foto hasil kerja (opsional)
) {
    companion object {
        const val MASUK = "MASUK"
        const val PULANG = "PULANG"
    }
}
