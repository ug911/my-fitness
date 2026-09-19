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

/**
 * Version 4 adds the coach and the nutrition calculator: a table for published
 * knowledge documents, one for the corrections you make to a food's numbers, and the
 * two historical records - the sets you lifted and the food you logged. Nothing
 * existing is touched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_3_4_SQL.forEach(db::execSQL)
    }
}

/** Kept separate so the statements can be run against a plain SQLite engine in a test. */
val MIGRATION_3_4_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `knowledge_docs` (`key` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
        "`docId` TEXT NOT NULL, `name` TEXT NOT NULL, `json` TEXT NOT NULL, " +
        "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`key`))",
    "CREATE INDEX IF NOT EXISTS `index_knowledge_docs_kind` ON `knowledge_docs` (`kind`)",
    "CREATE TABLE IF NOT EXISTS `food_overrides` (`foodId` TEXT NOT NULL, `portionLabel` TEXT, " +
        "`kcal` REAL, `protein` REAL, `carbs` REAL, `fat` REAL, `updatedAtMillis` INTEGER NOT NULL, " +
        "PRIMARY KEY(`foodId`))",
    "CREATE TABLE IF NOT EXISTS `workout_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`date` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `setIndex` INTEGER NOT NULL, " +
        "`weightKg` REAL, `reps` INTEGER NOT NULL, `note` TEXT, `doneAtMillis` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_workout_sets_date` ON `workout_sets` (`date`)",
    "CREATE INDEX IF NOT EXISTS `index_workout_sets_exerciseId_date` ON `workout_sets` (`exerciseId`, `date`)",
    "CREATE TABLE IF NOT EXISTS `food_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`date` TEXT NOT NULL, `foodId` TEXT NOT NULL, `meal` TEXT NOT NULL, `portions` REAL NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_food_log_date` ON `food_log` (`date`)",
    "CREATE INDEX IF NOT EXISTS `index_food_log_date_meal` ON `food_log` (`date`, `meal`)",
)
