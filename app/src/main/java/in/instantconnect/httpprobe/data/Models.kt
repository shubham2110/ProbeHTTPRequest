package `in`.instantconnect.httpprobe.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "probe_configs")
data class ProbeConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val method: String, // GET or POST
    val payload: String?,
    val intervalSeconds: Long,
    val lastNetworkState: String? = null,
    val onlyOnNetworkChange: Boolean = false,
    val isActive: Boolean = true,
    val username: String? = null,
    val password: String? = null,
    val customHeaders: String? = null, // JSON string of headers
    val readSms: Boolean = false,
    val isNtfy: Boolean = false,
    val ntfyUrl: String? = null,
    val ntfyUsername: String? = null,
    val ntfyPassword: String? = null,
    val lastNtfyMessageId: String? = null,
    val ntfyConsumerMode: Boolean = false,
    val ntfySince: String? = "all", // "all", "latest", "10m", etc.
    val ntfyScheduled: Boolean = false,
    val ntfyFilters: String? = null,
    val smsFilterRegex: String? = null
)

@Entity(tableName = "probe_logs")
data class ProbeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val configId: Long,
    val timestamp: Long,
    val message: String,
    val success: Boolean,
    val networkInfo: String? = null
)
