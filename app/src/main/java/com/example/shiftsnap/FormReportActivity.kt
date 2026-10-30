package com.example.shiftsnap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.shiftsnap.databinding.ActivityFormReportBinding
import com.example.shiftsnap.model.ReportHistory
import com.example.shiftsnap.model.ReportRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Tahap 3 – ShiftSnap
 * FormReportActivity.kt
 *
 * Form input kondisi mesin dengan fitur kamera nyata menggunakan
 * ActivityResultContracts.TakePicture + FileProvider.
 */
class FormReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFormReportBinding
    private lateinit var session: SessionManager

    // URI file foto yang akan diisi kamera
    private var fotoUri: Uri? = null
    private var isFotoAttached = false

    // ── Konstanta key Intent ──────────────────────────────────────────────────
    companion object {
        const val EXTRA_EDIT_REPORT_ID = "extra_edit_report_id"
        const val EXTRA_NAMA_MESIN   = "extra_nama_mesin"
        const val EXTRA_SHIFT        = "extra_shift"
        const val EXTRA_STATUS_MESIN = "extra_status_mesin"
        const val EXTRA_CEK_PELUMAS  = "extra_cek_pelumas"
        const val EXTRA_CEK_SUHU     = "extra_cek_suhu"
        const val EXTRA_CEK_LISTRIK  = "extra_cek_listrik"
        const val EXTRA_CEK_BERSIH   = "extra_cek_bersih"
        const val EXTRA_CATATAN      = "extra_catatan"
        const val EXTRA_FOTO         = "extra_foto"
        const val EXTRA_FOTO_URI     = "extra_foto_uri"
        const val EXTRA_OPERATOR_ID  = "extra_operator_id"

        private val SHIFT_OPTIONS = listOf(
            "Shift 1 – Pagi  (06.00–14.00)",
            "Shift 2 – Siang (14.00–22.00)",
            "Shift 3 – Malam (22.00–06.00)"
        )
    }

    // ── ActivityResult: ambil foto dari kamera ────────────────────────────────

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && fotoUri != null) {
                isFotoAttached = true
                updateFotoIndicator()
            } else {
                // User membatalkan atau kamera gagal
                fotoUri = null
            }
        }

    // ── ActivityResult: minta izin CAMERA ────────────────────────────────────

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto", Toast.LENGTH_SHORT).show()
            }
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        binding = ActivityFormReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupShiftSpinner()
        setupFotoButton()
        setupSimpanButton()
        checkEditMode()
    }

    private fun checkEditMode() {
        val reportId = intent.getStringExtra(EXTRA_EDIT_REPORT_ID) ?: return
        val report = ReportRepository.getById(reportId) ?: return

        binding.etNamaMesin.setText(report.namaMesin)
        binding.spinnerShift.setText(report.shift, false)

        when {
            report.statusMesin.contains("Normal", ignoreCase = true) -> binding.rbNormal.isChecked = true
            report.statusMesin.contains("Maintenance", ignoreCase = true) -> binding.rbMaintenance.isChecked = true
            report.statusMesin.contains("Rusak", ignoreCase = true) ||
                report.statusMesin.contains("Breakdown", ignoreCase = true) -> binding.rbBreakdown.isChecked = true
        }

        binding.cbPelumas.isChecked    = report.cekPelumas
        binding.cbSuhu.isChecked       = report.cekSuhu
        binding.cbKelistrikan.isChecked = report.cekListrik
        binding.cbKebersihan.isChecked  = report.cekBersih
        binding.etCatatan.setText(report.catatan)

        if (report.fotoUri.isNotBlank()) {
            fotoUri = Uri.parse(report.fotoUri)
            isFotoAttached = true
        } else {
            isFotoAttached = report.fotoTerlampir
        }
        updateFotoIndicator()
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.navigationIcon?.mutate()?.also { icon ->
            icon.setTint(getColor(android.R.color.tab_indicator_text))
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
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

    // ── Spinner Shift ─────────────────────────────────────────────────────────

    private fun setupShiftSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            SHIFT_OPTIONS
        )
        binding.spinnerShift.setAdapter(adapter)
        binding.spinnerShift.setOnClickListener { binding.spinnerShift.showDropDown() }
    }

    // ── Tombol Foto ───────────────────────────────────────────────────────────

    private fun setupFotoButton() {
        binding.btnAmbilFoto.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED -> launchCamera()
                else -> requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /** Buat file sementara lalu buka kamera. */
    private fun launchCamera() {
        val photoFile = createImageFile()
        val uri = FileProvider.getUriForFile(
            this,
            "com.example.shiftsnap.fileprovider",
            photoFile
        )
        fotoUri = uri
        takePictureLauncher.launch(uri)
    }

    /** Buat file JPEG kosong di folder Pictures. */
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile("FOTO_${timeStamp}_", ".jpg", storageDir)
    }

    private fun updateFotoIndicator() {
        if (isFotoAttached) {
            binding.layoutFotoEmpty.visibility = android.view.View.GONE
            binding.chipFotoAttached.visibility = android.view.View.VISIBLE
            binding.btnAmbilFoto.text = "Ganti Foto Kondisi Mesin"

            // Tampilkan preview foto jika URI tersedia
            fotoUri?.let { uri ->
                binding.imgFotoPreview.visibility = android.view.View.VISIBLE
                binding.imgFotoPreview.setImageURI(uri)
            }
        } else {
            binding.layoutFotoEmpty.visibility  = android.view.View.VISIBLE
            binding.chipFotoAttached.visibility = android.view.View.GONE
            binding.imgFotoPreview.visibility   = android.view.View.GONE
            binding.btnAmbilFoto.text = "Ambil Foto Kondisi Mesin"
        }
    }

    // ── Validasi & Navigasi ───────────────────────────────────────────────────

    private fun setupSimpanButton() {
        binding.btnSimpan.setOnClickListener {
            if (validateForm()) navigateToDetail()
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        val namaMesin = binding.etNamaMesin.text?.toString()?.trim() ?: ""
        if (namaMesin.isEmpty()) {
            binding.tilNamaMesin.error = "Kode/Nama Mesin wajib diisi"
            isValid = false
        } else {
            binding.tilNamaMesin.error = null
        }

        val shift = binding.spinnerShift.text?.toString()?.trim() ?: ""
        if (shift.isEmpty()) {
            binding.tilShift.error = "Pilih shift terlebih dahulu"
            isValid = false
        } else {
            binding.tilShift.error = null
        }

        if (binding.rgStatusMesin.checkedRadioButtonId == -1) {
            binding.tvStatusError.visibility = android.view.View.VISIBLE
            isValid = false
        } else {
            binding.tvStatusError.visibility = android.view.View.GONE
        }

        val statusChecked = binding.rgStatusMesin.checkedRadioButtonId
        val catatan = binding.etCatatan.text?.toString()?.trim() ?: ""
        if (statusChecked == R.id.rbMaintenance || statusChecked == R.id.rbBreakdown) {
            if (catatan.isEmpty()) {
                binding.tilCatatan.error = "Catatan wajib diisi jika mesin tidak normal"
                isValid = false
            } else {
                binding.tilCatatan.error = null
            }
        } else {
            binding.tilCatatan.error = null
        }

        return isValid
    }

    private fun navigateToDetail() {
        val statusLabel = when (binding.rgStatusMesin.checkedRadioButtonId) {
            R.id.rbNormal      -> "✅  Normal / Siap Jalan"
            R.id.rbMaintenance -> "⚠️  Perlu Maintenance"
            R.id.rbBreakdown   -> "🔴  Rusak / Breakdown"
            else               -> "-"
        }

        val bundle = Bundle().apply {
            putString(EXTRA_NAMA_MESIN,   binding.etNamaMesin.text?.toString()?.trim())
            putString(EXTRA_SHIFT,        binding.spinnerShift.text?.toString()?.trim())
            putString(EXTRA_STATUS_MESIN, statusLabel)
            putBoolean(EXTRA_CEK_PELUMAS, binding.cbPelumas.isChecked)
            putBoolean(EXTRA_CEK_SUHU,    binding.cbSuhu.isChecked)
            putBoolean(EXTRA_CEK_LISTRIK, binding.cbKelistrikan.isChecked)
            putBoolean(EXTRA_CEK_BERSIH,  binding.cbKebersihan.isChecked)
            putString(EXTRA_CATATAN,      binding.etCatatan.text?.toString()?.trim())
            putBoolean(EXTRA_FOTO,        isFotoAttached)
            putString(EXTRA_FOTO_URI,     fotoUri?.toString() ?: "")
            putString(EXTRA_OPERATOR_ID,  session.getOperatorId())
        }

        // Simpan ke ReportRepository
        val editId = intent.getStringExtra(EXTRA_EDIT_REPORT_ID)
        val report = ReportHistory(
            id            = editId ?: UUID.randomUUID().toString(),
            namaMesin     = bundle.getString(EXTRA_NAMA_MESIN, ""),
            shift         = bundle.getString(EXTRA_SHIFT, ""),
            statusMesin   = statusLabel,
            cekPelumas    = bundle.getBoolean(EXTRA_CEK_PELUMAS),
            cekSuhu       = bundle.getBoolean(EXTRA_CEK_SUHU),
            cekListrik    = bundle.getBoolean(EXTRA_CEK_LISTRIK),
            cekBersih     = bundle.getBoolean(EXTRA_CEK_BERSIH),
            catatan       = bundle.getString(EXTRA_CATATAN, ""),
            fotoTerlampir = isFotoAttached,
            fotoUri       = fotoUri?.toString() ?: "",
            operatorId    = session.getOperatorId()
        )
        if (editId != null) ReportRepository.update(report) else ReportRepository.add(report)

        val intent = Intent(this, ReportDetailActivity::class.java).apply { putExtras(bundle) }
        startActivity(intent)
    }
}
