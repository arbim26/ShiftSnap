package com.example.shiftsnap

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
        setCheckRow(binding.tvDetailPelumas, report.cekPelumas, "Cek Pelumas / Oli")
        setCheckRow(binding.tvDetailSuhu,    report.cekSuhu,    "Cek Suhu Mesin")
        setCheckRow(binding.tvDetailListrik, report.cekListrik, "Cek Modul Kelistrikan")
        setCheckRow(binding.tvDetailBersih,  report.cekBersih,  "Kebersihan Area Mesin")

        // Foto
        val shown = report.fotoTerlampir && PhotoLoader.load(binding.imgDetailFoto, report.fotoUri)
        binding.imgDetailFoto.visibility = if (shown) View.VISIBLE else View.GONE
        binding.tvDetailFoto.text = when {
            shown                  -> "Foto terlampir"
            report.fotoTerlampir   -> "Foto terlampir (file tidak dapat dimuat)"
            else                   -> "— Tidak ada foto —"
        }

        // Catatan
        binding.tvDetailCatatan.text = report.catatan.ifBlank { "— Tidak ada catatan kendala —" }
    }

    /** Baris daftar periksa: ikon centang/silang (drawable) + label, tanpa emoji. */
    private fun setCheckRow(tv: TextView, checked: Boolean, label: String) {
        tv.text = label
        tv.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (checked) R.drawable.ic_check else R.drawable.ic_close, 0, 0, 0
        )
    }

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
            append("  - Pelumas / Oli    : ${if (report.cekPelumas) "Sudah" else "Belum"}\n")
            append("  - Suhu Mesin       : ${if (report.cekSuhu) "Sudah" else "Belum"}\n")
            append("  - Modul Kelistrikan: ${if (report.cekListrik) "Sudah" else "Belum"}\n")
            append("  - Kebersihan Area  : ${if (report.cekBersih) "Sudah" else "Belum"}\n")
        }
        return """
*LAPORAN KONDISI MESIN – ShiftSnap*
━━━━━━━━━━━━━━━━━━━━
Mesin     : ${report.namaMesin}
Shift     : ${report.shift}
Operator  : ${report.operatorId}
Waktu     : ${sdf.format(Date(report.timestamp))} WIB
Status    : ${report.statusMesin}

*Daftar Periksa:*
$checklist
Foto      : ${if (report.fotoTerlampir) "Terlampir" else "Tidak ada"}
Catatan   : ${report.catatan.ifBlank { "Tidak ada catatan" }}
━━━━━━━━━━━━━━━━━━━━
Dikirim via ShiftSnap App
        """.trimIndent()
    }
}
