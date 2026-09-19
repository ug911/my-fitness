package com.ug911.myfitness.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ug911.myfitness.data.local.dao.AiAnalysisDao
import com.ug911.myfitness.data.local.dao.EntryDao
import com.ug911.myfitness.data.local.dao.FoodLogDao
import com.ug911.myfitness.data.local.dao.KnowledgeDao
import com.ug911.myfitness.data.local.dao.WorkoutDao
import com.ug911.myfitness.data.local.dao.JournalDao
import com.ug911.myfitness.data.local.dao.PlanDao
import com.ug911.myfitness.data.local.dao.TrackerDao
import com.ug911.myfitness.data.local.entity.AiAnalysisEntity
import com.ug911.myfitness.data.local.entity.EntryEntity
import com.ug911.myfitness.data.local.entity.FoodLogEntity
import com.ug911.myfitness.data.local.entity.FoodOverrideEntity
import com.ug911.myfitness.data.local.entity.KnowledgeDocEntity
import com.ug911.myfitness.data.local.entity.WorkoutSetEntity
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
        KnowledgeDocEntity::class,
        FoodOverrideEntity::class,
        WorkoutSetEntity::class,
        FoodLogEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MyFitnessDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao
    abstract fun entryDao(): EntryDao
    abstract fun journalDao(): JournalDao
    abstract fun planDao(): PlanDao
    abstract fun aiAnalysisDao(): AiAnalysisDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun foodLogDao(): FoodLogDao

    companion object {
        private const val NAME = "my-fitness.db"

        fun build(context: Context): MyFitnessDatabase =
            Room.databaseBuilder(context, MyFitnessDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
