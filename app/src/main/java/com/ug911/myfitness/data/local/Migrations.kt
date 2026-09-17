package com.ug911.myfitness.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2 turns the abstract Exercise/Food/Behaviour categories into sections that
 * follow the day, and adds a standing per-tracker target. Nothing already logged is
 * touched: entries keep pointing at their trackers, and trackers that are no longer
 * part of the day are switched off rather than deleted.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trackers ADD COLUMN targetValue REAL")
        TrackerSeed.migrateToPersonalDay { sql, args -> db.execSQL(sql, args) }
    }
}
