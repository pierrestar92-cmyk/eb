package de.pierre.ebesuchermonitor

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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

    private fun pruneOldSnapshots() {
        val keepAfter = System.currentTimeMillis() - RETENTION_MS
        writableDatabase.delete("snapshots", "recorded_at < ?", arrayOf(keepAfter.toString()))
    }

    companion object {
        private const val DATABASE_NAME = "ebesucher_monitor.db"
        private const val DATABASE_VERSION = 1
        private const val RETENTION_MS = 90L * 24L * 60L * 60L * 1000L
    }
}
