package `in`.instantconnect.httpprobe.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProbeDao {
    @Query("SELECT * FROM probe_configs WHERE isActive = 1")
    fun getAllActiveConfigs(): List<ProbeConfig>

    @Query("SELECT * FROM probe_configs")
    fun getAllConfigsFlow(): Flow<List<ProbeConfig>>

    @Query("SELECT * FROM probe_configs")
    suspend fun getAllConfigsSync(): List<ProbeConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ProbeConfig)

    @Update
    suspend fun updateConfig(config: ProbeConfig)

    @Delete
    suspend fun deleteConfig(config: ProbeConfig)

    @Insert
    suspend fun insertLog(log: ProbeLog)

    @Query("SELECT * FROM probe_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<ProbeLog>>

    @Query("DELETE FROM probe_logs WHERE id NOT IN (SELECT id FROM probe_logs ORDER BY timestamp DESC LIMIT 100)")
    suspend fun trimLogs()

    @Query("SELECT * FROM probe_configs WHERE id = :id")
    suspend fun getConfigById(id: Long): ProbeConfig?

    @Query("SELECT * FROM probe_logs WHERE configId = :configId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLogForConfig(configId: Long): ProbeLog?

    @Query("SELECT MAX(timestamp) FROM probe_logs WHERE configId = :configId AND success = 1")
    suspend fun getLastSuccessTimestamp(configId: Long): Long?
}
