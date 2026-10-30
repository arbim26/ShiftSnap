package com.example.shiftsnap.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.shiftsnap.R
import com.example.shiftsnap.databinding.ItemReportHistoryBinding
import com.example.shiftsnap.model.ReportHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView Adapter untuk daftar riwayat laporan di RecapListActivity.
 *
 * Fitur:
 *  - Card item menarik dengan chip status warna-warni
 *  - Overflow menu (PopupMenu) per item: Edit, Hapus, Kirim Ulang
 *  - DiffUtil agar animasi update efisien
 */
class ReportHistoryAdapter(
    private val onItemClick: (ReportHistory) -> Unit,
    private val onEdit:      (ReportHistory) -> Unit,
    private val onDelete:    (ReportHistory) -> Unit,
    private val onResend:    (ReportHistory) -> Unit
) : ListAdapter<ReportHistory, ReportHistoryAdapter.ViewHolder>(DiffCallback) {

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    private object DiffCallback : DiffUtil.ItemCallback<ReportHistory>() {
        override fun areItemsTheSame(old: ReportHistory, new: ReportHistory) =
            old.id == new.id
        override fun areContentsTheSame(old: ReportHistory, new: ReportHistory) =
            old == new
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    inner class ViewHolder(
        private val binding: ItemReportHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val sdf = SimpleDateFormat("dd MMM yyyy  •  HH:mm", Locale("id", "ID"))

        fun bind(report: ReportHistory, context: Context) {
            // Header
            binding.tvMesinName.text    = report.namaMesin
            binding.tvShift.text        = report.shift
            binding.tvTimestamp.text    = sdf.format(Date(report.timestamp))
            binding.tvOperator.text     = "Operator: ${report.operatorId}"

            // Chip status
            binding.chipStatus.text = report.statusMesin
            val (bgColor, textColor) = when {
                report.statusMesin.contains("Normal", ignoreCase = true) ->
                    R.color.status_normal_bg    to R.color.status_normal_text
                report.statusMesin.contains("Maintenance", ignoreCase = true) ->
                    R.color.status_warning_bg   to R.color.status_warning_text
                else ->
                    R.color.status_error_bg     to R.color.status_error_text
            }
            binding.chipStatus.setChipBackgroundColorResource(bgColor)
            binding.chipStatus.setTextColor(ContextCompat.getColor(context, textColor))

            // Checklist summary (icon saja agar compact)
            val checks = buildString {
                if (report.cekPelumas)  append("🛢️ ")
                if (report.cekSuhu)     append("🌡️ ")
                if (report.cekListrik)  append("⚡ ")
                if (report.cekBersih)  append("🧹 ")
                if (isEmpty()) append("Tidak ada")
            }
            binding.tvChecklist.text = "Diperiksa: $checks"

            // Foto
            binding.tvFoto.text = if (report.fotoTerlampir) "📷 Ada foto" else "Tanpa foto"

            // Catatan preview
            binding.tvCatatanPreview.text = report.catatan.ifBlank { "– Tidak ada catatan –" }

            // Click seluruh card → buka detail
            binding.root.setOnClickListener { onItemClick(report) }

            // Tombol overflow menu (⋮)
            binding.btnOverflow.setOnClickListener { view ->
                PopupMenu(context, view).apply {
                    menuInflater.inflate(R.menu.menu_report_item, menu)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_edit    -> { onEdit(report);   true }
                            R.id.action_delete  -> { onDelete(report); true }
                            R.id.action_resend  -> { onResend(report); true }
                            else                -> false
                        }
                    }
                    show()
                }
            }
        }
    }

    // ── Adapter overrides ─────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReportHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), holder.itemView.context)
    }
}
