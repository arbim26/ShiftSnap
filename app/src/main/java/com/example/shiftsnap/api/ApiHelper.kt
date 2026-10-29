package com.example.shiftsnap.api

import android.util.Log
import com.example.shiftsnap.model.ReportHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ═══════════════════════════════════════════════════════════════════════════════
// ApiHelper.kt
// Modul: com.example.shiftsnap.api
//
// Kumpulan fungsi helper untuk memanggil ApiService dari Activity/Fragment
// tanpa boilerplate berulang.
//
// Cara penggunaan di Activity:
//
//   sendReportToBackend(
//       apiService = ApiClient.apiService,
//       report     = reportHistory,
//       onSuccess  = { response -> showSnackbar("Terkirim: ${response.reportId}") },
//       onError    = { message  -> showSnackbar("Gagal: $message") }
//   )
// ═══════════════════════════════════════════════════════════════════════════════

private const val TAG = "ShiftSnap-API"

/**
 * Mengonversi [ReportHistory] (model in-memory) ke [MachineReportRequest] (DTO API).
 */
fun ReportHistory.toApiRequest(): MachineReportRequest =
    MachineReportRequest(
        operatorId    = operatorId,
        machineName   = namaMesin,
        shift         = shift,
        machineStatus = statusMesin,
        checklist     = ChecklistDto(
            lubricantOil = cekPelumas,
            temperature  = cekSuhu,
            electrical   = cekListrik,
            cleanliness  = cekBersih
        ),
        notes         = catatan,
        hasPhoto      = fotoTerlampir,
        reportedAt    = timestamp
    )

/**
 * Mengirim satu laporan ke backend menggunakan Coroutine.
 *
 * @param apiService  Instance [ApiService] dari [ApiClient].
 * @param report      Laporan yang akan dikirim.
 * @param scope       CoroutineScope pemanggil (defaultnya lifecycleScope Activity).
 * @param onSuccess   Callback sukses – dipanggil di Main thread dengan response body.
 * @param onError     Callback gagal  – dipanggil di Main thread dengan pesan error.
 */
fun sendReportToBackend(
    apiService: ApiService,
    report:     ReportHistory,
    scope:      CoroutineScope = CoroutineScope(Dispatchers.Main),
    onSuccess:  (MachineReportResponse) -> Unit,
    onError:    (String) -> Unit
) {
    scope.launch {
        try {
            Log.d(TAG, "Mengirim laporan: ${report.namaMesin} – ${report.id}")

            // Jalankan network call di IO thread, hasilnya di-switch ke Main
            val response = withContext(Dispatchers.IO) {
                apiService.submitReport(report.toApiRequest())
            }

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Log.i(TAG, "Laporan berhasil dikirim. Server ID: ${body.reportId}")
                    onSuccess(body)
                } else {
                    val msg = body?.message ?: "Respons server tidak valid"
                    Log.w(TAG, "Server menolak laporan: $msg")
                    onError(msg)
                }
            } else {
                val msg = "HTTP ${response.code()} – ${response.message()}"
                Log.e(TAG, "Gagal: $msg")
                onError(msg)
            }

        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Koneksi gagal"
            Log.e(TAG, "Exception saat mengirim laporan", e)
            onError(msg)
        }
    }
}

/**
 * Contoh penggunaan standalone (dapat dipanggil dari ViewModel atau Activity):
 *
 * ```kotlin
 * // Di dalam Activity, dengan lifecycleScope:
 * binding.btnKirimServer.setOnClickListener {
 *     sendReportToBackend(
 *         apiService = ApiClient.apiService,
 *         report     = currentReport,
 *         scope      = lifecycleScope,
 *         onSuccess  = { resp ->
 *             Toast.makeText(this, "Berhasil! ID: ${resp.reportId}", Toast.LENGTH_SHORT).show()
 *         },
 *         onError    = { msg ->
 *             Toast.makeText(this, "Gagal: $msg", Toast.LENGTH_LONG).show()
 *         }
 *     )
 * }
 * ```
 */
@Suppress("unused", "KDocUnresolvedReference")
fun usageExample() { /* lihat komentar di atas */ }
