package com.connectivity.checker.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.connectivity.checker.R
import com.connectivity.checker.databinding.ItemCheckResultBinding
import com.connectivity.checker.model.CheckResult
import com.connectivity.checker.model.CheckStatus
import com.connectivity.checker.model.CheckType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CheckResultAdapter(
    private val onRunClick:  (Int) -> Unit,
    private val onEditClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : ListAdapter<CheckResult, CheckResultAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(
        private val b: ItemCheckResultBinding
    ) : RecyclerView.ViewHolder(b.root) {

        private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun bind(result: CheckResult, position: Int) {
            val cfg = result.config

            b.tvName.text    = cfg.name
            b.chipType.text  = cfg.type.name

            b.tvDetails.text = when (cfg.type) {
                CheckType.ICMP -> cfg.host ?: "—"
                CheckType.DNS  -> buildString {
                    append(cfg.host ?: "—")
                    cfg.dnsServer?.let { append(" via $it") }
                }
                CheckType.HTTP -> "${cfg.method} ${cfg.url ?: "—"}"
                CheckType.TCP  -> "${cfg.host ?: "—"}:${cfg.port ?: "—"}"
                CheckType.UDP  -> "${cfg.host ?: "—"}:${cfg.port ?: "—"} (UDP)"
                CheckType.TLS  -> buildString {
                    append("${cfg.host ?: "—"}:${cfg.port ?: 443}")
                    if (cfg.sni != null && cfg.sni != cfg.host) append(" SNI=${cfg.sni}")
                }
            }

            val intervalHint = if (cfg.interval > 0) " ⟳${cfg.interval}s" else ""
            b.tvDetails.text = b.tvDetails.text.toString() + intervalHint

            val (colorRes, resultText, latencyText) = when (result.status) {
                CheckStatus.PENDING -> Triple(R.color.status_pending, "Not checked yet", "")
                CheckStatus.RUNNING -> Triple(R.color.status_running, "Checking…", "")
                CheckStatus.SUCCESS -> Triple(
                    R.color.status_success, result.message,
                    if (result.latencyMs >= 0) "${result.latencyMs} ms" else ""
                )
                CheckStatus.FAILURE -> Triple(
                    R.color.status_failure, result.message,
                    if (result.latencyMs >= 0) "${result.latencyMs} ms" else ""
                )
            }

            val color = ContextCompat.getColor(b.root.context, colorRes)
            b.statusIndicator.backgroundTintList = ColorStateList.valueOf(color)
            b.tvResult.text   = resultText
            b.tvLatency.text  = latencyText
            b.tvLastChecked.text = if (result.lastChecked > 0)
                timeFmt.format(Date(result.lastChecked)) else ""

            b.btnRun.isEnabled = result.status != CheckStatus.RUNNING
            b.btnRun.setOnClickListener { onRunClick(position) }

            b.btnMore.setOnClickListener { view ->
                PopupMenu(view.context, view).apply {
                    menuInflater.inflate(R.menu.menu_check_item, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_edit   -> { onEditClick(position);   true }
                            R.id.action_delete -> { onDeleteClick(position); true }
                            else -> false
                        }
                    }
                    show()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCheckResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), position)

    class DiffCallback : DiffUtil.ItemCallback<CheckResult>() {
        override fun areItemsTheSame(old: CheckResult, new: CheckResult) =
            old.config.name == new.config.name && old.config.type == new.config.type
        override fun areContentsTheSame(old: CheckResult, new: CheckResult) = old == new
    }
}
