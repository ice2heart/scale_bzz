package ru.scalejournal.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "measurements", indices = [androidx.room.Index("timeMillis")])
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val weightKg: Double,
    val impedanceOhm: Double?,
    val source: String,
)

@Entity(tableName = "profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val heightCm: Int,
    val birthMillis: Long,
    val isMale: Boolean,
)

@Dao
interface MeasurementDao {
    @Insert
    suspend fun insert(m: Measurement): Long

    @Delete
    suspend fun delete(m: Measurement)

    @Query("SELECT * FROM measurements ORDER BY timeMillis DESC")
    fun observeAll(): Flow<List<Measurement>>
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)
}

@Database(
    entities = [Measurement::class, UserProfile::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun profileDao(): ProfileDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS profile (" +
                        "id INTEGER NOT NULL PRIMARY KEY, " +
                        "heightCm INTEGER NOT NULL, " +
                        "birthMillis INTEGER NOT NULL, " +
                        "isMale INTEGER NOT NULL)",
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scalejournal.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
