package com.example.shiftsnap.model

/**
 * Repository sederhana berbasis in-memory list.
 * Berfungsi sebagai "single source of truth" selama sesi aplikasi.
 *
 * Tahap produksi: ganti implementasi ini dengan Room Database atau
 * panggilan ke ApiService (lihat modul api/).
 */
object ReportRepository {

    private val _reports = mutableListOf<ReportHistory>()

    /** Daftar laporan (terbaru di atas). */
    val reports: List<ReportHistory>
        get() = _reports.sortedByDescending { it.timestamp }

    /** Tambah laporan baru. */
    fun add(report: ReportHistory) {
        _reports.add(0, report)
    }

    /** Hapus laporan berdasarkan ID. */
    fun delete(id: String) {
        _reports.removeAll { it.id == id }
    }

    /** Ambil laporan berdasarkan ID (untuk Edit). */
    fun getById(id: String): ReportHistory? = _reports.find { it.id == id }

    /** Update laporan yang sudah ada (untuk Edit). */
    fun update(updated: ReportHistory) {
        val idx = _reports.indexOfFirst { it.id == updated.id }
        if (idx != -1) _reports[idx] = updated
    }
}
