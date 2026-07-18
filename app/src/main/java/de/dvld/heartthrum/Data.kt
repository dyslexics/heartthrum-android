package de.dvld.heartthrum

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pulse_records")
data class PulseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val bpm: Int,
    val tag: String?,
    val mood: Int?
)

@Dao
interface PulseDao {
    @Insert
    suspend fun insert(record: PulseRecord)

    @Delete
    suspend fun delete(record: PulseRecord)

    @Query("SELECT * FROM pulse_records ORDER BY date DESC")
    fun all(): Flow<List<PulseRecord>>
}

@Database(entities = [PulseRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pulseDao(): PulseDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "heartthrum.db"
                ).build().also { instance = it }
            }
    }
}

enum class MeasureTag(val key: String) {
    RESTING("resting"),
    AFTER_ACTIVITY("afterActivity"),
    MORNING("morning"),
    EVENING("evening");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}
