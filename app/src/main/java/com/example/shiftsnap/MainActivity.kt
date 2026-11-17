package com.example.shiftsnap

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.shiftsnap.databinding.ActivityMainBinding
import com.example.shiftsnap.model.AttendanceRecord
import com.example.shiftsnap.model.AttendanceRepository
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var session: SessionManager
    private lateinit var attendanceRepo: AttendanceRepository

    private val jamFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        attendanceRepo = AttendanceRepository(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupUser()
        setupClickListeners()
    }

    // Refresh status absensi setiap kali halaman utama tampil lagi
    // (termasuk setelah kembali dari AttendanceActivity).
    override fun onResume() {
        super.onResume()
        binding.tvTanggal.text = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
            .format(Date())
        refreshAttendanceStatus()
    }

    // Inflate menu via onCreateOptionsMenu (cara yang benar)
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // Handle klik menu item
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_dark_mode -> {
                val newMode = !session.isDarkMode()
                session.setDarkMode(newMode)
                AppCompatDelegate.setDefaultNightMode(
                    if (newMode) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                val msg = if (newMode) "Mode Gelap aktif" else "Mode Terang aktif"
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                true
            }
            R.id.menu_logout -> {
                confirmLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
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

    private fun setupUser() {
        val operatorId = session.getOperatorId()
        binding.tvOperatorName.text = if (operatorId.isNotEmpty()) operatorId else "Operator"
    }

    private fun setupClickListeners() {
        binding.cardAttendance.setOnClickListener { navigateToAttendance() }
        binding.cardNewReport.setOnClickListener { navigateToNewReport() }
        binding.cardRecap.setOnClickListener { navigateToRecap() }
    }

    // ── Absensi ─────────────────────────────────────────────

    /** Isi kartu "Absensi Hari Ini" dan ubah kartu aksi sesuai kondisi. */
    private fun refreshAttendanceStatus() {
        val id = session.getOperatorId()
        val masuk = attendanceRepo.todayMasuk(id)
        val pulang = attendanceRepo.todayPulang(id)

        binding.tvAbsenMasukInfo.text =
            masuk?.let { "Pukul ${jamFormat.format(Date(it.timestamp))}" } ?: "Belum absen masuk"
        binding.tvAbsenPulangInfo.text =
            pulang?.let { "Pukul ${jamFormat.format(Date(it.timestamp))}" } ?: "Belum absen pulang"


        // Chip status
        val (label, bg, fg) = when {
            pulang != null -> Triple("Selesai", R.color.status_normal_bg, R.color.status_normal_text)
            masuk != null -> Triple(
                "Sedang bekerja",
                R.color.md_theme_primaryContainer,
                R.color.md_theme_onPrimaryContainer
            )
            else -> Triple("Belum absen", R.color.status_warning_bg, R.color.status_warning_text)
        }
        binding.chipAbsenState.text = label
        binding.chipAbsenState.setChipBackgroundColorResource(bg)
        binding.chipAbsenState.setTextColor(getColor(fg))

        // Kartu aksi: Masuk → Pulang → Selesai (terkunci)
        val selesai = pulang != null
        binding.cardAttendance.alpha = if (selesai) 0.5f else 1f
        binding.tvAttendanceTitle.text = when {
            selesai -> "Absensi Selesai"
            masuk != null -> "Absen Pulang"
            else -> "Absen Masuk"
        }
        binding.tvAttendanceSubtitle.text = when {
            selesai -> "Sampai jumpa besok"
            masuk != null -> "Isi pekerjaan hari ini"
            else -> "Dokumentasi Absen"
        }
    }

    private fun navigateToAttendance() {
        val operatorId = session.getOperatorId()

        // Sudah absen pulang → absensi hari ini selesai, tidak bisa absen lagi
        if (attendanceRepo.todayPulang(operatorId) != null) {
            Snackbar.make(
                binding.root,
                "Kamu sudah absen masuk dan pulang hari ini",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        // Sudah masuk → buka tab Pulang, kalau belum → tab Masuk
        val jenis = if (attendanceRepo.todayMasuk(operatorId) != null)
            AttendanceRecord.PULANG else AttendanceRecord.MASUK

        val intent = Intent(this, AttendanceActivity::class.java).apply {
            putExtra(
                AttendanceActivity.EXTRA_OPERATOR_NAME,
                if (operatorId.isNotEmpty()) operatorId else "Operator"
            )
            putExtra(AttendanceActivity.EXTRA_JENIS, jenis)
        }
        startActivity(intent)
    }

    private fun navigateToNewReport() {
        startActivity(Intent(this, FormReportActivity::class.java))
    }

    private fun navigateToRecap() {
        startActivity(Intent(this, RecapListActivity::class.java))
    }

}