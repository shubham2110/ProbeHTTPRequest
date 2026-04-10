package `in`.instantconnect.httpprobe

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import `in`.instantconnect.httpprobe.data.ProbeLog
import `in`.instantconnect.httpprobe.databinding.ItemProbeLogBinding
import java.text.SimpleDateFormat
import java.util.*

class ProbeLogAdapter : ListAdapter<ProbeLog, ProbeLogAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProbeLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = getItem(position)
        holder.bind(log)
    }

    inner class ViewHolder(private val binding: ItemProbeLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: ProbeLog) {
            binding.textViewTimestamp.text = dateFormat.format(Date(log.timestamp))
            binding.textViewMessage.text = log.message
            binding.textViewNetworkInfo.text = log.networkInfo ?: "No network info"
            val color = if (log.success) android.R.color.black else android.R.color.holo_red_dark
            binding.textViewMessage.setTextColor(binding.root.context.getColor(color))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ProbeLog>() {
        override fun areItemsTheSame(oldItem: ProbeLog, newItem: ProbeLog) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ProbeLog, newItem: ProbeLog) = oldItem == newItem
    }
}
