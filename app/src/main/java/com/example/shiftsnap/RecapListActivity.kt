package com.example.shiftsnap

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shiftsnap.adapter.ReportHistoryAdapter
import com.example.shiftsnap.api.ApiClient
import com.example.shiftsnap.api.sendReportToBackend
import com.example.shiftsnap.databinding.ActivityRecapListBinding
import com.example.shiftsnap.model.ReportHistory
import com.example.shiftsnap.model.ReportRepository
import com.google.android.material.snackbar.Snackbar

/**
 * Tahap 3 – ShiftSnap
 * RecapListActivity.kt
 *
 * Menampilkan riwayat seluruh laporan yang sudah dikirim dari FormReportActivity.
 * Data disimpan di ReportRepository (in-memory). Produksi: ganti dengan Room DB.
 *
 * Fitur:
 *  - RecyclerView + card item dengan PopupMenu (Edit, Hapus, Kirim Ulang)
 *  - Empty-state view saat daftar kosong
 *  - FAB untuk buat laporan baru
 *  - Implicit Intent "Bagikan ke Supervisor" (Intent.ACTION_SEND)
 */
class RecapListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecapListBinding
    private lateinit var adapter: ReportHistoryAdapter
    private lateinit var session: SessionManager

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        binding = ActivityRecapListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        // Refresh setiap kali kembali (misal setelah edit laporan)
        refreshList()
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Riwayat Laporan"
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_dark_mode -> {
                    val newMode = !session.isDarkMode()
                    session.setDarkMode(newMode)
                    AppCompatDelegate.setDefaultNightMode(
                        if (newMode) AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    )
                    true
                }
                R.id.menu_logout -> {
                    confirmLogout()
                    true
                }
                else -> false
            }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Yakin ingin keluar dari akun ini?")
            .setPositiveButton("Logout") { _, _ ->
                session.logout()
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ReportHistoryAdapter(
            onItemClick = { report -> openDetail(report) },
            onEdit      = { report -> openEdit(report) },
            onDelete    = { report -> confirmDelete(report) },
            onResend    = { report -> shareReport(report) }
        )
        binding.rvReports.layoutManager = LinearLayoutManager(this)
        binding.rvReports.adapter       = adapter
    }

    private fun refreshList() {
        val list = ReportRepository.reports
        adapter.submitList(list.toList())   // .toList() agar DiffUtil mendeteksi perubahan

        // Tampilkan empty-state jika tidak ada data
        binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvReports.visibility   = if (list.isEmpty()) View.GONE   else View.VISIBLE
    }

    // ── FAB ───────────────────────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabNewReport.setOnClickListener {
            startActivity(Intent(this, FormReportActivity::class.java))
        }
    }

    // ── Navigasi Detail / Edit ────────────────────────────────────────────────

    /** Buka DetailReportActivity (read-only) */
    private fun openDetail(report: ReportHistory) {
        val intent = Intent(this, DetailReportActivity::class.java).apply {
            putExtra(DetailReportActivity.EXTRA_REPORT_ID, report.id)
        }
        startActivity(intent)
    }

    /** Buka FormReportActivity dalam mode Edit */
    private fun openEdit(report: ReportHistory) {
        val intent = Intent(this, FormReportActivity::class.java).apply {
            putExtra(FormReportActivity.EXTRA_EDIT_REPORT_ID, report.id)
        }
        startActivity(intent)
    }

    // ── Hapus Laporan ─────────────────────────────────────────────────────────

    private fun confirmDelete(report: ReportHistory) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Laporan?")
            .setMessage("Laporan mesin \"${report.namaMesin}\" akan dihapus permanen.")
            .setPositiveButton("Hapus") { _, _ ->
                ReportRepository.delete(report.id)
                refreshList()
                Snackbar.make(binding.root, "Laporan dihapus", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ── Implicit Intent: Bagikan Laporan ke Supervisor ────────────────────────

    /**
     * Menggunakan Intent.ACTION_SEND untuk berbagi teks rangkuman laporan.
     * Pengguna dapat memilih aplikasi tujuan (WhatsApp, Email, Telegram, dll.).
     */
    private fun shareReport(report: ReportHistory) {
        val text = buildShareText(report)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type    = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Laporan Mesin ${report.namaMesin} – ShiftSnap")
            putExtra(Intent.EXTRA_TEXT, text)
        }

        startActivity(
            Intent.createChooser(
                shareIntent,
                "Bagikan Laporan via..."
            )
        )
    }

    /** Susun teks rangkuman laporan untuk dibagikan. */
    private fun buildShareText(report: ReportHistory): String {
        val checklist = buildString {
            append(if (report.cekPelumas)  "  ✅ Pelumas/Oli\n" else "  ❌ Pelumas/Oli\n")
            append(if (report.cekSuhu)     "  ✅ Suhu Mesin\n"   else "  ❌ Suhu Mesin\n")
            append(if (report.cekListrik)  "  ✅ Kelistrikan\n"  else "  ❌ Kelistrikan\n")
            append(if (report.cekBersih)   "  ✅ Kebersihan\n"   else "  ❌ Kebersihan\n")
        }
        val foto = if (report.fotoTerlampir) "Ada (terlampir)" else "Tidak ada"

        return """
🏭 *LAPORAN KONDISI MESIN – ShiftSnap*
━━━━━━━━━━━━━━━━━━━━
📌 Mesin     : ${report.namaMesin}
🕐 Shift     : ${report.shift}
👷 Operator  : ${report.operatorId}
📊 Status    : ${report.statusMesin}

📋 *Daftar Periksa:*
$checklist
📷 Foto      : $foto
📝 Catatan   : ${report.catatan.ifBlank { "Tidak ada catatan" }}
━━━━━━━━━━━━━━━━━━━━
Dikirim via ShiftSnap App
        """.trimIndent()
    }

    // ── (Opsional) Kirim ke Backend via Retrofit ──────────────────────────────

    /**
     * Contoh penggunaan ApiService – panggil saat tombol "Kirim ke Server" ditekan.
     * Di Tahap produksi, panggil ini setelah laporan selesai diisi.
     */
    @Suppress("unused")
    private fun sendToBackend(report: ReportHistory) {
        sendReportToBackend(
            apiService = ApiClient.apiService,
            report     = report,
            onSuccess  = { response ->
                Snackbar.make(
                    binding.root,
                    "✅ Terkirim ke server (ID: ${response.reportId})",
                    Snackbar.LENGTH_LONG
                ).show()
            },
            onError = { message ->
                Snackbar.make(binding.root, "❌ Gagal: $message", Snackbar.LENGTH_LONG).show()
            }
        )
    }
}
