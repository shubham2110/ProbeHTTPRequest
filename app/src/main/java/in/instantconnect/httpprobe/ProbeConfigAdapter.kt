package `in`.instantconnect.httpprobe

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import `in`.instantconnect.httpprobe.data.ProbeConfig
import `in`.instantconnect.httpprobe.databinding.ItemProbeConfigBinding

class ProbeConfigAdapter(
    private val onHitNowClick: (ProbeConfig) -> Unit,
    private val onEditClick: (ProbeConfig) -> Unit,
    private val onLogsClick: (ProbeConfig) -> Unit,
    private val onDeleteClick: (ProbeConfig) -> Unit
) : ListAdapter<ProbeConfig, ProbeConfigAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProbeConfigBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = getItem(position)
        holder.bind(config)
    }

    inner class ViewHolder(private val binding: ItemProbeConfigBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(config: ProbeConfig) {
            binding.textViewUrl.text = "${config.method}: ${config.url}"
            binding.textViewDetails.text = "Interval: ${config.intervalSeconds}s | Network Change Only: ${config.onlyOnNetworkChange}"
            
            binding.buttonHitNow.setOnClickListener { onHitNowClick(config) }
            binding.buttonEdit.setOnClickListener { onEditClick(config) }
            binding.buttonLogs.setOnClickListener { onLogsClick(config) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(config) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ProbeConfig>() {
        override fun areItemsTheSame(oldItem: ProbeConfig, newItem: ProbeConfig) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ProbeConfig, newItem: ProbeConfig) = oldItem == newItem
    }
}
