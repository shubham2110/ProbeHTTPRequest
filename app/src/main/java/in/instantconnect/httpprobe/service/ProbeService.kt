package `in`.instantconnect.httpprobe.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import `in`.instantconnect.httpprobe.MainActivity
import `in`.instantconnect.httpprobe.data.AppDatabase
import `in`.instantconnect.httpprobe.data.ProbeConfig
import `in`.instantconnect.httpprobe.data.ProbeLog
import kotlinx.coroutines.*
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ProbeService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val CHANNEL_ID = "ProbeServiceChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification("Probing service is running")
        startForeground(NOTIFICATION_ID, notification)
        startProbing()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "HTTP Probe Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HTTP Probe Service")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startProbing() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val configs = db.probeDao().getAllActiveConfigs()
                    
                    db.probeDao().trimLogs()

                    configs.forEach { config ->
                        launch {
                            processProbe(config)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                delay(10000)
            }
        }
    }

    private val lastRunMap = mutableMapOf<Long, Long>()

    private suspend fun processProbe(config: ProbeConfig, isManual: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        val lastRun = lastRunMap[config.id] ?: 0L
        
        if (!isManual && (currentTime - lastRun < config.intervalSeconds * 1000)) {
            return
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val fullNetworkInfo = getDetailedNetworkInfo()
        val relevantStateForChangeDetection = getRelevantNetworkState(fullNetworkInfo)

        val lastLog = db.probeDao().getLastLogForConfig(config.id)
        val lastFailed = lastLog != null && !lastLog.success
        val lastSuccessTime = db.probeDao().getLastSuccessTimestamp(config.id) ?: 0L

        var currentConfig = config
        var ntfyEvents: List<NtfyMessage> = emptyList()

        // 1. Fetch ntfy events if enabled
        if (currentConfig.isNtfy) {
            ntfyEvents = fetchNtfyEvents(currentConfig)
            if (ntfyEvents.isNotEmpty()) {
                // If in Consumer Mode, log the events immediately
                if (currentConfig.ntfyConsumerMode) {
                    // Log in chronological order (oldest first)
                    ntfyEvents.reversed().forEach { msg ->
                        db.probeDao().insertLog(ProbeLog(
                            configId = currentConfig.id,
                            timestamp = System.currentTimeMillis(),
                            message = "[ntfy Event] ID: ${msg.id}\n${msg.message}",
                            success = true,
                            networkInfo = fullNetworkInfo
                        ))
                    }
                }
                // Update the local currentConfig object with the latest ID so we don't repeat
                currentConfig = currentConfig.copy(lastNtfyMessageId = ntfyEvents.first().id)
            }
        }

        // Fetch new SMS messages if enabled
        val smsList = if (currentConfig.readSms) readLastSmsAfter(lastSuccessTime, currentConfig.smsFilterRegex) else emptyList()

        if (!isManual) {
            // Trigger logic for API hits:
            var shouldTrigger = false

            if (currentConfig.isNtfy && !currentConfig.ntfyConsumerMode) {
                // ntfy Trigger mode
                if (ntfyEvents.isNotEmpty() || lastFailed) shouldTrigger = true
            } else if (currentConfig.readSms) {
                // SMS Trigger mode
                if (smsList.isNotEmpty() || lastFailed) shouldTrigger = true
            } else if (currentConfig.onlyOnNetworkChange) {
                // Network Trigger mode
                if (currentConfig.lastNetworkState != relevantStateForChangeDetection || lastFailed) shouldTrigger = true
            } else {
                // Standard interval mode (if no specific trigger is set, or if consumer mode is on but we have a URL)
                shouldTrigger = true
            }

            if (!shouldTrigger) {
                // Even if not triggering API, we must update the config state (like lastNtfyMessageId or lastNetworkState)
                db.probeDao().updateConfig(currentConfig.copy(lastNetworkState = relevantStateForChangeDetection))
                lastRunMap[currentConfig.id] = currentTime
                return
            }
        }

        // Update the database with new state before/after hit
        db.probeDao().updateConfig(currentConfig.copy(lastNetworkState = relevantStateForChangeDetection))

        // If URL is blank, we stop here (Consumer mode only)
        if (currentConfig.url.isBlank()) {
            lastRunMap[currentConfig.id] = currentTime
            return
        }

        // Prepare URL and Payload with variables
        var finalUrl = currentConfig.url
        var finalPayload = currentConfig.payload
        
        if (currentConfig.readSms) {
            val sms1 = smsList.getOrNull(0) ?: ""
            val sms2 = smsList.getOrNull(1) ?: ""
            finalUrl = finalUrl.replace("%SMS1%", URLEncoder.encode(sms1, "UTF-8"))
                               .replace("%SMS2%", URLEncoder.encode(sms2, "UTF-8"))
            finalPayload = finalPayload?.replace("%SMS1%", sms1)?.replace("%SMS2%", sms2)
        }

        if (currentConfig.isNtfy && ntfyEvents.isNotEmpty()) {
            // Use the single latest message for the API hit
            val msg = ntfyEvents.first().message
            finalUrl = finalUrl.replace("%NTFY%", URLEncoder.encode(msg, "UTF-8"))
            finalPayload = finalPayload?.replace("%NTFY%", msg)
        }

        // Execute API Probe
        try {
            val requestBuilder = Request.Builder().url(finalUrl)
            
            val u = currentConfig.username
            val p = currentConfig.password
            if (!u.isNullOrBlank() && !p.isNullOrBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(u, p))
            }

            currentConfig.customHeaders?.let { headersJson ->
                try {
                    val json = JSONObject(headersJson)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        requestBuilder.header(key, json.getString(key))
                    }
                } catch (e: Exception) {}
            }

            if (currentConfig.method.equals("POST", ignoreCase = true)) {
                val body = finalPayload?.toRequestBody("application/json".toMediaType()) 
                    ?: "".toRequestBody("text/plain".toMediaType())
                requestBuilder.post(body)
            } else {
                requestBuilder.get()
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val success = response.isSuccessful
                val responseBody = response.body?.string() ?: ""
                val ntfyPrefix = if (currentConfig.isNtfy && ntfyEvents.isNotEmpty()) "[ntfy: ${ntfyEvents.first().message}] " else ""
                val message = "$ntfyPrefix Status: ${response.code}. Response: ${responseBody.take(200)}"
                
                db.probeDao().insertLog(ProbeLog(
                    configId = currentConfig.id,
                    timestamp = currentTime,
                    message = if (isManual) "[Manual] $message" else message,
                    success = success,
                    networkInfo = fullNetworkInfo
                ))
            }
        } catch (e: Exception) {
            db.probeDao().insertLog(ProbeLog(
                configId = currentConfig.id,
                timestamp = currentTime,
                message = "Error: ${e.message}",
                success = false,
                networkInfo = fullNetworkInfo
            ))
        }

        lastRunMap[currentConfig.id] = currentTime
    }

    private data class NtfyMessage(val id: String, val message: String)

    private fun fetchNtfyEvents(config: ProbeConfig): List<NtfyMessage> {
        val baseUrl = config.ntfyUrl ?: return emptyList()
        val messages = mutableListOf<NtfyMessage>()
        try {
            // Build the URL for polling new messages
            var requestUrl = if (baseUrl.contains("/json")) {
                baseUrl
            } else {
                val base = if (baseUrl.contains("?")) baseUrl.substringBefore("?") else baseUrl
                val query = if (baseUrl.contains("?")) baseUrl.substringAfter("?") else ""
                val cleanBase = if (base.endsWith("/")) base.dropLast(1) else base
                if (query.isNotEmpty()) "$cleanBase/json?$query" else "$cleanBase/json"
            }

            // Always add poll=1
            requestUrl += if (requestUrl.contains("?")) "&poll=1" else "?poll=1"
            
            // Add since parameter
            // Logic: 
            // 1. If we have a lastNtfyMessageId from a previous SUCCESSFUL local poll, use it as 'since' to get only truly new ones.
            // 2. Otherwise, use the user-defined 'ntfySince' (default "all").
            val sinceParam = if (!config.lastNtfyMessageId.isNullOrBlank()) {
                config.lastNtfyMessageId
            } else {
                config.ntfySince ?: "all"
            }
            requestUrl += "&since=$sinceParam"

            // Add scheduled parameter if enabled
            if (config.ntfyScheduled) {
                requestUrl += "&scheduled=1"
            }

            // Add user defined filters if any
            if (!config.ntfyFilters.isNullOrBlank()) {
                val filters = config.ntfyFilters!!
                requestUrl += if (filters.startsWith("&") || filters.startsWith("?")) filters else "&$filters"
            }

            val requestBuilder = Request.Builder().url(requestUrl)
            if (!config.ntfyUsername.isNullOrBlank() && !config.ntfyPassword.isNullOrBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(config.ntfyUsername, config.ntfyPassword))
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val lines = body.split("\n")
                    val newMessages = mutableListOf<NtfyMessage>()
                    for (line in lines) {
                        if (line.isBlank()) continue
                        try {
                            val json = JSONObject(line)
                            if (json.optString("event") == "message") {
                                val id = json.getString("id")
                                val message = json.getString("message")
                                // Double check to avoid duplicates
                                if (id != config.lastNtfyMessageId) {
                                    newMessages.add(NtfyMessage(id, message))
                                }
                            }
                        } catch (e: Exception) {}
                    }
                    
                    // Only take the last 5 new messages to avoid pressure
                    val latestMessages = newMessages.takeLast(5)
                    messages.addAll(latestMessages)

                    // Throw notification for consumer mode if any new messages
                    if (config.ntfyConsumerMode && latestMessages.isNotEmpty()) {
                        latestMessages.forEach { msg ->
                            showNotification("ntfy: ${config.ntfyUrl?.substringAfterLast("/")}", msg.message)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Return sorted by ID descending (newest first)
        return messages.sortedByDescending { it.id }
    }

    private fun showNotification(title: String, content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
            
        notificationManager.notify(notificationId, notification)
    }

    private fun readLastSmsAfter(timestamp: Long, regex: String? = null): List<String> {
        val messages = mutableListOf<String>()
        try {
            val cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                "date > ?",
                arrayOf(timestamp.toString()),
                "date DESC"
            )
            cursor?.use {
                val addrIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                var count = 0
                val pattern = regex?.let { r -> try { Regex(r, RegexOption.IGNORE_CASE) } catch (e: Exception) { null } }
                
                while (it.moveToNext() && count < 2) {
                    val address = it.getString(addrIndex) ?: ""
                    val body = it.getString(bodyIndex) ?: ""
                    
                    if (pattern != null) {
                        if (pattern.containsMatchIn(address) || pattern.containsMatchIn(body)) {
                            messages.add(body)
                            count++
                        }
                    } else {
                        messages.add(body)
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return messages
    }

    private fun getRelevantNetworkState(fullInfo: String): String {
        return fullInfo.substringBefore(" | Speed:")
    }

    private fun getDetailedNetworkInfo(): String {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "Disconnected"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"
        val linkProperties = connectivityManager.getLinkProperties(network)
        
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Other"
        }
        
        val ipv4Addresses = linkProperties?.linkAddresses
            ?.filter { it.address is java.net.Inet4Address }
            ?.joinToString { it.address.hostAddress ?: "" } ?: "N/A"
            
        val gateway = linkProperties?.routes
            ?.filter { it.isDefaultRoute && it.gateway is java.net.Inet4Address }
            ?.joinToString { it.gateway?.hostAddress ?: "" } ?: "N/A"
            
        val dnsServers = linkProperties?.dnsServers
            ?.filter { it is java.net.Inet4Address }
            ?.joinToString { it.hostAddress ?: "" } ?: "N/A"
        
        val upstream = capabilities.linkUpstreamBandwidthKbps
        val downstream = capabilities.linkDownstreamBandwidthKbps
        
        return "$transport | IP: $ipv4Addresses | GW: $gateway | DNS: $dnsServers | Speed: ${upstream}/${downstream}kbps"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_HIT_NOW") {
            val configId = intent.getLongExtra("EXTRA_CONFIG_ID", -1L)
            if (configId != -1L) {
                serviceScope.launch {
                    try {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val config = db.probeDao().getConfigById(configId)
                        config?.let { processProbe(it, isManual = true) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
