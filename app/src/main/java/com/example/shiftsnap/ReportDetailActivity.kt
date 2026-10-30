package com.example.shiftsnap

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.example.shiftsnap.databinding.ActivityReportDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tahap 2 – ShiftSnap
 * ReportDetailActivity.kt
 *
 * Halaman rekapitulasi / pratinjau laporan shift.
 * Menerima seluruh data dari FormReportActivity via Explicit Intent Bundle.
 */
class ReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        populateData()
        setupButtons()
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    // ── Isi Data dari Intent ──────────────────────────────────────────────────

    private fun populateData() {
        val extras = intent.extras ?: return

        // Timestamp
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))
        binding.tvTimestamp.text = sdf.format(Date())

        // Identitas
        setRow(binding.rowMesin,    "Nama Mesin",  extras.getString(FormReportActivity.EXTRA_NAMA_MESIN, "-"))
        setRow(binding.rowShift,    "Shift",       extras.getString(FormReportActivity.EXTRA_SHIFT, "-"))
        setRow(binding.rowOperator, "Operator",    extras.getString(FormReportActivity.EXTRA_OPERATOR_ID, "-"))

        // Status Mesin
        binding.chipStatusMesin.text = extras.getString(FormReportActivity.EXTRA_STATUS_MESIN, "-")

        // Daftar Periksa – tambahkan Chip untuk setiap item yang dicentang
        val checklistItems = mapOf(
            FormReportActivity.EXTRA_CEK_PELUMAS  to "Pelumas / Oli",
            FormReportActivity.EXTRA_CEK_SUHU     to "Suhu Mesin",
            FormReportActivity.EXTRA_CEK_LISTRIK  to "Modul Kelistrikan",
            FormReportActivity.EXTRA_CEK_BERSIH   to "Kebersihan Area"
        )

        var anyChecked = false
        checklistItems.forEach { (key, label) ->
            val isChecked = extras.getBoolean(key, false)
            val chip = Chip(this).apply {
                text = if (isChecked) "✅ $label" else "⬜ $label"
                isCheckable  = false
                isClickable  = false
                // Chip berwarna berbeda berdasarkan status
                if (isChecked) {
                    setChipBackgroundColorResource(R.color.md_theme_primaryContainer)
                    setTextColor(getColor(R.color.md_theme_onPrimaryContainer))
                } else {
                    setChipBackgroundColorResource(R.color.md_theme_surfaceVariant)
                    setTextColor(getColor(R.color.md_theme_onSurfaceVariant))
                }
            }
            binding.chipGroupChecklist.addView(chip)
            if (isChecked) anyChecked = true
        }

        if (!anyChecked) {
            // Tampilkan placeholder jika tidak ada yang dicentang
            val chip = Chip(this).apply {
                text = "Tidak ada item yang diperiksa"
                isCheckable = false
                isClickable = false
            }
            binding.chipGroupChecklist.addView(chip)
        }

        // Catatan
        val catatan = extras.getString(FormReportActivity.EXTRA_CATATAN, "").orEmpty()
        binding.tvCatatan.text = catatan.ifEmpty { "– Tidak ada catatan kendala –" }

        // Foto
        val isFoto = extras.getBoolean(FormReportActivity.EXTRA_FOTO, false)
        val fotoUri = extras.getString(FormReportActivity.EXTRA_FOTO_URI, "").orEmpty()
        val shown = isFoto && PhotoLoader.load(binding.imgFotoDetail, fotoUri)
        binding.imgFotoDetail.visibility = if (shown) View.VISIBLE else View.GONE
        binding.tvFotoStatus.text = when {
            shown  -> "📷 Foto terlampir"
            isFoto -> "📷 Foto terlampir (file tidak dapat dimuat)"
            else   -> "– Tidak ada foto –"
        }
    }

    /**
     * Helper: isi label & value pada included item_detail_row.
     * ViewBinding mengakses child view lewat tag ID yang sudah diset di include.
     */
    private fun setRow(rowBinding: com.example.shiftsnap.databinding.ItemDetailRowBinding, label: String, value: String) {
        rowBinding.tvLabel.text = label
        rowBinding.tvValue.text = value
    }

    // ── Tombol Aksi ───────────────────────────────────────────────────────────

    private fun setupButtons() {
        // Kembali & Edit: tutup activity ini, kembali ke FormReportActivity
        binding.btnKembaliEdit.setOnClickListener {
            finish()
        }

        // Selesai: kembali ke MainActivity (clear back-stack Form + Detail)
        binding.btnSelesai.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }
}
