package com.example.shiftsnap.model

import java.util.UUID

/**
 * Model in-memory untuk satu entri riwayat laporan shift.
 * Digunakan oleh RecapListActivity + ReportHistoryAdapter.
 *
 * Di Tahap produksi, class ini dapat digantikan oleh Room Entity
 * atau DTO dari backend (lihat MachineReport.kt di modul api/).
 */
data class ReportHistory(
    val id: String = UUID.randomUUID().toString(),
    val namaMesin: String,
    val shift: String,
    val statusMesin: String,
    val cekPelumas: Boolean,
    val cekSuhu: Boolean,
    val cekListrik: Boolean,
    val cekBersih: Boolean,
    val catatan: String,
    val fotoTerlampir: Boolean,
    val fotoUri: String = "",
    val operatorId: String,
    val timestamp: Long = System.currentTimeMillis()   // epoch millis
)
