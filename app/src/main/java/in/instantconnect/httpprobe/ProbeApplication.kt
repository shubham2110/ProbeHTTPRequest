package `in`.instantconnect.httpprobe

import android.app.Application
import `in`.instantconnect.httpprobe.data.AppDatabase
import `in`.instantconnect.httpprobe.data.ProbeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProbeApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = throwable.stackTraceToString()
            
            // Try to log the crash to the database
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val dao = AppDatabase.getDatabase(this@ProbeApplication).probeDao()
                    dao.insertLog(
                        ProbeLog(
                            configId = -1, // Use -1 for system/app crashes
                            timestamp = System.currentTimeMillis(),
                            message = "CRASH in thread ${thread.name}:\n$stackTrace",
                            success = false,
                            networkInfo = "N/A"
                        )
                    )
                } catch (e: Exception) {
                    // If database logging fails, nothing more we can do
                } finally {
                    // We can't wait for the above coroutine to finish reliably here
                }
            }
            // Give it a tiny bit of time before calling the default handler
            Thread.sleep(200)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
