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

/**
 * Version 3 is housekeeping from real use: the breakfast item was Poha rather than
 * Bohara, the school run does not need its own leaving and returning times, and lunch
 * and dinner get their own food checklists. Old entries are rewritten only where a name
 * changed, and the retired trackers are switched off rather than dropped.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val executor = TrackerSeed.Executor { sql, args -> db.execSQL(sql, args) }
        TrackerSeed.renameOption(executor, from = "Bohara", to = "Poha")
        TrackerSeed.retire(executor, "Left for school")
        TrackerSeed.retire(executor, "Back from school")
        TrackerSeed.insertMissing(executor, PersonalDay.trackers())
    }
}
