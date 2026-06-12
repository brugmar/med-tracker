package com.medtracker.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MedTrackerDao {

    @Query("SELECT * FROM medicines ORDER BY name COLLATE NOCASE")
    fun medicines(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines ORDER BY id")
    suspend fun allMedicinesForBackup(): List<Medicine>

    @Insert
    suspend fun insertMedicine(medicine: Medicine): Long

    @Insert
    suspend fun insertMedicines(medicines: List<Medicine>)

    @Update
    suspend fun updateMedicine(medicine: Medicine)

    @Delete
    suspend fun deleteMedicine(medicine: Medicine)

    @Insert
    suspend fun insertLog(log: DoseLog): Long

    @Insert
    suspend fun insertLogs(logs: List<DoseLog>)

    @Delete
    suspend fun deleteLog(log: DoseLog)

    @Update
    suspend fun updateLog(log: DoseLog)

    @Query("SELECT * FROM dose_logs WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun logsBetween(start: Long, end: Long): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs ORDER BY timestamp ASC, id ASC")
    suspend fun allLogsForBackup(): List<DoseLog>

    @Query(
        "SELECT * FROM dose_logs WHERE medicineId = :medicineId " +
            "AND timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC"
    )
    fun logsForMedicineBetween(medicineId: Long, start: Long, end: Long): Flow<List<DoseLog>>

    @Query(
        "SELECT * FROM dose_logs WHERE medicineId = :medicineId " +
            "AND timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC, id ASC"
    )
    suspend fun logsForMedicineOnce(medicineId: Long, start: Long, end: Long): List<DoseLog>

    @Query("DELETE FROM dose_logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM medicines")
    suspend fun deleteAllMedicines()

    @Transaction
    suspend fun replaceAllData(medicines: List<Medicine>, doseLogs: List<DoseLog>) {
        deleteAllLogs()
        deleteAllMedicines()
        insertMedicines(medicines)
        insertLogs(doseLogs)
    }
}

@Database(entities = [Medicine::class, DoseLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dao(): MedTrackerDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medtracker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medicines_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        defaultAmount REAL NOT NULL,
                        unit TEXT NOT NULL,
                        presetAmount1 REAL NOT NULL,
                        presetAmount2 REAL NOT NULL,
                        presetAmount3 REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO medicines_new (
                        id,
                        name,
                        defaultAmount,
                        unit,
                        presetAmount1,
                        presetAmount2,
                        presetAmount3
                    )
                    SELECT
                        id,
                        name,
                        defaultAmount,
                        unit,
                        defaultAmount * 0.5,
                        defaultAmount,
                        defaultAmount * 2
                    FROM medicines
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE medicines")
                db.execSQL("ALTER TABLE medicines_new RENAME TO medicines")
            }
        }
    }
}
