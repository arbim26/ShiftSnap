package com.example.shiftsnap.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ═══════════════════════════════════════════════════════════════════════════════
// ApiService.kt
// Modul: com.example.shiftsnap.api
//
// Retrofit interface – mendefinisikan seluruh endpoint REST API ShiftSnap.
// Base URL dikonfigurasi di ApiClient.kt.
//
// Konvensi:
//  - Fungsi bersifat suspend → dipanggil dari Coroutine (lifecycleScope / viewModelScope)
//  - Kembalikan Response<T> agar status HTTP dapat diperiksa (isSuccessful, code)
// ═══════════════════════════════════════════════════════════════════════════════

interface ApiService {

    // ── Laporan ───────────────────────────────────────────────────────────────

    /**
     * POST /reports
     * Mengirimkan laporan kondisi mesin baru ke server.
     *
     * @param report Body request laporan (JSON).
     * @return Response berisi [MachineReportResponse] dari server.
     */
    @POST("reports")
    suspend fun submitReport(
        @Body report: MachineReportRequest
    ): Response<MachineReportResponse>

    /**
     * GET /reports
     * Mengambil seluruh daftar laporan milik satu operator.
     *
     * @param operatorId ID operator yang mengirim laporan.
     * @param page       Halaman ke-n (default 1) untuk paginasi.
     * @param limit      Jumlah item per halaman (default 20).
     */
    @GET("reports")
    suspend fun getReports(
        @Query("operator_id") operatorId: String,
        @Query("page")        page:       Int = 1,
        @Query("limit")       limit:      Int = 20
    ): Response<ReportListResponse>

    /**
     * GET /reports/{id}
     * Mengambil satu laporan berdasarkan ID server.
     *
     * @param reportId ID unik laporan yang ditetapkan server.
     */
    @GET("reports/{id}")
    suspend fun getReportById(
        @Path("id") reportId: String
    ): Response<MachineReportResponse>
}
