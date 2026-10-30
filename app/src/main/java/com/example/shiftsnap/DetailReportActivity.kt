package com.example.shiftsnap

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.shiftsnap.databinding.ActivityDetailReportBinding
import com.example.shiftsnap.model.ReportHistory
import com.example.shiftsnap.model.ReportRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tahap 3 – ShiftSnap
 * DetailReportActivity.kt
 *
 * Menampilkan kartu rangkuman detail satu laporan dari riwayat.
 * Menerima [EXTRA_REPORT_ID] → mengambil data dari ReportRepository.
 *
 * Fitur:
 *  - Tampilan lengkap seluruh field laporan
 *  - Tombol "Bagikan ke Supervisor" via Implicit Intent (Intent.ACTION_SEND)
 *  - Tombol "Edit Laporan"
 */
class DetailReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailReportBinding
    private lateinit var session: SessionManager

    companion object {
        const val EXTRA_REPORT_ID = "extra_report_id"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        binding = ActivityDetailReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reportId = intent.getStringExtra(EXTRA_REPORT_ID)
        val report   = reportId?.let { ReportRepository.getById(it) }

        if (report == null) {
            finish()    // Data tidak ditemukan – kembali
            return
        }

        setupToolbar(report)
        populateCard(report)
        setupButtons(report)
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbar(report: ReportHistory) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Detail Laporan"
        supportActionBar?.subtitle = report.namaMesin
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.main_menu)
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

    // ── Isi Data ──────────────────────────────────────────────────────────────

    private fun populateCard(report: ReportHistory) {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))

        // Header card
        binding.tvDetailMesin.text     = report.namaMesin
        binding.tvDetailShift.text     = report.shift
        binding.tvDetailTimestamp.text = sdf.format(Date(report.timestamp))
        binding.tvDetailOperator.text  = "Operator: ${report.operatorId}"

        // Status chip
        binding.chipDetailStatus.text = report.statusMesin
        val (bgRes, txtRes) = when {
            report.statusMesin.contains("Normal", ignoreCase = true) ->
                R.color.status_normal_bg  to R.color.status_normal_text
            report.statusMesin.contains("Maintenance", ignoreCase = true) ->
                R.color.status_warning_bg to R.color.status_warning_text
            else ->
                R.color.status_error_bg   to R.color.status_error_text
        }
        binding.chipDetailStatus.setChipBackgroundColorResource(bgRes)
        binding.chipDetailStatus.setTextColor(ContextCompat.getColor(this, txtRes))

        // Checklist
        binding.tvDetailPelumas.text  = checkIcon(report.cekPelumas)  + " Pelumas / Oli"
        binding.tvDetailSuhu.text     = checkIcon(report.cekSuhu)     + " Suhu Mesin"
        binding.tvDetailListrik.text  = checkIcon(report.cekListrik)  + " Modul Kelistrikan"
        binding.tvDetailBersih.text   = checkIcon(report.cekBersih)   + " Kebersihan Area"

        // Foto
        val shown = report.fotoTerlampir && PhotoLoader.load(binding.imgDetailFoto, report.fotoUri)
        binding.imgDetailFoto.visibility = if (shown) View.VISIBLE else View.GONE
        binding.tvDetailFoto.text = when {
            shown                  -> "📷 Foto terlampir"
            report.fotoTerlampir   -> "📷 Foto terlampir (file tidak dapat dimuat)"
            else                   -> "— Tidak ada foto —"
        }

        // Catatan
        binding.tvDetailCatatan.text = report.catatan.ifBlank { "— Tidak ada catatan kendala —" }
    }

    private fun checkIcon(checked: Boolean) = if (checked) "✅" else "⬜"

    // ── Tombol Aksi ───────────────────────────────────────────────────────────

    private fun setupButtons(report: ReportHistory) {

        // ── Implicit Intent: Bagikan ke Supervisor ────────────────────────────
        binding.btnBagikan.setOnClickListener {
            val text = buildShareText(report)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Laporan Mesin ${report.namaMesin} – ShiftSnap")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Laporan ke Supervisor via..."))
        }

        // ── Edit Laporan ──────────────────────────────────────────────────────
        binding.btnEdit.setOnClickListener {
            val intent = Intent(this, FormReportActivity::class.java).apply {
                putExtra(FormReportActivity.EXTRA_EDIT_REPORT_ID, report.id)
            }
            startActivity(intent)
            finish()
        }
    }

    // ── Susun teks berbagi ────────────────────────────────────────────────────

    private fun buildShareText(report: ReportHistory): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        val checklist = buildString {
            append(if (report.cekPelumas) "  ✅ Pelumas/Oli\n"   else "  ❌ Pelumas/Oli\n")
            append(if (report.cekSuhu)    "  ✅ Suhu Mesin\n"    else "  ❌ Suhu Mesin\n")
            append(if (report.cekListrik) "  ✅ Kelistrikan\n"   else "  ❌ Kelistrikan\n")
            append(if (report.cekBersih)  "  ✅ Kebersihan\n"    else "  ❌ Kebersihan\n")
        }
        return """
🏭 *LAPORAN KONDISI MESIN – ShiftSnap*
━━━━━━━━━━━━━━━━━━━━
📌 Mesin     : ${report.namaMesin}
🕐 Shift     : ${report.shift}
👷 Operator  : ${report.operatorId}
📅 Waktu     : ${sdf.format(Date(report.timestamp))} WIB
📊 Status    : ${report.statusMesin}

📋 *Daftar Periksa:*
$checklist
📷 Foto      : ${if (report.fotoTerlampir) "Terlampir" else "Tidak ada"}
📝 Catatan   : ${report.catatan.ifBlank { "Tidak ada catatan" }}
━━━━━━━━━━━━━━━━━━━━
Dikirim via ShiftSnap App
        """.trimIndent()
    }
}
