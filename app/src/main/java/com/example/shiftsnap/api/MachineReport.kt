package com.example.shiftsnap.api

import com.google.gson.annotations.SerializedName

// ═══════════════════════════════════════════════════════════════════════════════
// MachineReport.kt
// Modul: com.example.shiftsnap.api
//
// Data class untuk konsumsi REST API backend ShiftSnap.
// Menggunakan Gson annotation agar nama field sesuai kontrak JSON backend.
//
// POST /reports  →  kirim MachineReportRequest
//                ←  terima MachineReportResponse
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Request body – dikirim ke endpoint POST /reports.
 */
data class MachineReportRequest(

    @SerializedName("operator_id")
    val operatorId: String,

    @SerializedName("machine_name")
    val machineName: String,

    @SerializedName("shift")
    val shift: String,

    @SerializedName("machine_status")
    val machineStatus: String,

    /** Daftar periksa sebagai peta key-boolean. */
    @SerializedName("checklist")
    val checklist: ChecklistDto,

    @SerializedName("notes")
    val notes: String,

    @SerializedName("has_photo")
    val hasPhoto: Boolean,

    /** Epoch millis – waktu laporan dibuat di device. */
    @SerializedName("reported_at")
    val reportedAt: Long
)

/**
 * DTO checklist – nested object di dalam request.
 */
data class ChecklistDto(
    @SerializedName("lubricant_oil")    val lubricantOil: Boolean,
    @SerializedName("temperature")      val temperature:  Boolean,
    @SerializedName("electrical")       val electrical:   Boolean,
    @SerializedName("cleanliness")      val cleanliness:  Boolean
)

/**
 * Response body – diterima dari backend setelah laporan berhasil disimpan.
 */
data class MachineReportResponse(

    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    /** ID unik laporan yang ditetapkan oleh server. */
    @SerializedName("report_id")
    val reportId: String,

    /** Timestamp server (ISO 8601). */
    @SerializedName("server_timestamp")
    val serverTimestamp: String
)

/**
 * Wrapper umum untuk respons API yang berisi list laporan (GET /reports).
 * Siap digunakan saat fitur "sinkronisasi riwayat dari server" ditambahkan.
 */
data class ReportListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data:    List<MachineReportResponse>
)
