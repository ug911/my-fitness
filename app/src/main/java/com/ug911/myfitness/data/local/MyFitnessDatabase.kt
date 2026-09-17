package com.ug911.myfitness.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ug911.myfitness.data.local.dao.AiAnalysisDao
import com.ug911.myfitness.data.local.dao.EntryDao
import com.ug911.myfitness.data.local.dao.JournalDao
import com.ug911.myfitness.data.local.dao.PlanDao
import com.ug911.myfitness.data.local.dao.TrackerDao
import com.ug911.myfitness.data.local.entity.AiAnalysisEntity
import com.ug911.myfitness.data.local.entity.EntryEntity
import com.ug911.myfitness.data.local.entity.JournalEntryEntity
import com.ug911.myfitness.data.local.entity.PlanEntity
import com.ug911.myfitness.data.local.entity.PlanTargetEntity
import com.ug911.myfitness.data.local.entity.TrackerEntity

@Database(
    entities = [
        TrackerEntity::class,
        EntryEntity::class,
        JournalEntryEntity::class,
        PlanEntity::class,
        PlanTargetEntity::class,
        AiAnalysisEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MyFitnessDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao
    abstract fun entryDao(): EntryDao
    abstract fun journalDao(): JournalDao
    abstract fun planDao(): PlanDao
    abstract fun aiAnalysisDao(): AiAnalysisDao

    companion object {
        private const val NAME = "my-fitness.db"

        fun build(context: Context): MyFitnessDatabase =
            Room.databaseBuilder(context, MyFitnessDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .addCallback(SeedCallback)
                .build()

        /**
         * Seeds the personal day on first run. Trackers are data, so the starter set is
         * a list of rows rather than anything baked into the schema.
         */
        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                TrackerSeed.seedFresh { sql, args -> db.execSQL(sql, args) }
            }
        }
    }
}
