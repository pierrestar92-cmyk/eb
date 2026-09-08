package de.pierre.ebesuchermonitor

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class DailySnapshotSummary(
    val dayStartMillis: Long,
    val totalBtp: Double?
)

class SnapshotDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE snapshots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recorded_at INTEGER NOT NULL,
                total_btp REAL NOT NULL,
                active_surfbars INTEGER NOT NULL,
                surfbar_count INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_snapshots_recorded_at ON snapshots(recorded_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS snapshots")
        onCreate(db)
    }

    fun insertSnapshot(totalBtp: Double, activeSurfbars: Int, surfbarCount: Int) {
        val values = ContentValues().apply {
            put("recorded_at", System.currentTimeMillis())
            put("total_btp", totalBtp)
            put("active_surfbars", activeSurfbars)
            put("surfbar_count", surfbarCount)
        }
        writableDatabase.insert("snapshots", null, values)
        pruneOldSnapshots()
    }

    fun latestTotalForToday(): Double? {
        val (start, end) = dayBounds(0)
        readableDatabase.rawQuery(
            "SELECT total_btp FROM snapshots WHERE recorded_at >= ? AND recorded_at < ? ORDER BY recorded_at DESC LIMIT 1",
            arrayOf(start.toString(), end.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getDouble(0) else null
        }
    }

    fun getDailyMaxEarnings(days: Int): List<DailySnapshotSummary> {
        val safeDays = days.coerceIn(1, 90)
        return (safeDays - 1 downTo 0).map { daysAgo ->
            val (start, end) = dayBounds(daysAgo)
            val total = readableDatabase.rawQuery(
                "SELECT MAX(total_btp) FROM snapshots WHERE recorded_at >= ? AND recorded_at < ?",
                arrayOf(start.toString(), end.toString())
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getDouble(0) else null
            }
            DailySnapshotSummary(dayStartMillis = start, totalBtp = total)
        }
    }

    private fun dayBounds(daysAgo: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance(BERLIN_TIME_ZONE, Locale.GERMANY).apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return start to calendar.timeInMillis
    }

    private fun pruneOldSnapshots() {
        val keepAfter = System.currentTimeMillis() - RETENTION_MS
        writableDatabase.delete("snapshots", "recorded_at < ?", arrayOf(keepAfter.toString()))
    }

    companion object {
        private val BERLIN_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Europe/Berlin")
        private const val DATABASE_NAME = "ebesucher_monitor.db"
        private const val DATABASE_VERSION = 1
        private const val RETENTION_MS = 90L * 24L * 60L * 60L * 1000L
    }
}
