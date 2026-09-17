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
import com.ug911.myfitness.data.local.entity.toEntity

@Database(
    entities = [
        TrackerEntity::class,
        EntryEntity::class,
        JournalEntryEntity::class,
        PlanEntity::class,
        PlanTargetEntity::class,
        AiAnalysisEntity::class,
    ],
    version = 1,
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
                .addCallback(SeedCallback)
                .build()

        /**
         * Seeds the default tracker set on first run. Trackers are data, so the starter
         * set is a list of rows rather than anything baked into the schema.
         */
        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val converters = Converters()
                DefaultTrackers.all().map { it.toEntity() }.forEach { tracker ->
                    db.execSQL(
                        "INSERT INTO trackers (name, category, type, unit, active, sortOrder, options, " +
                            "ratingMax, healthMetric, direction, aggregation) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        arrayOf(
                            tracker.name,
                            tracker.category.name,
                            tracker.type.name,
                            tracker.unit,
                            if (tracker.active) 1 else 0,
                            tracker.sortOrder,
                            converters.listToString(tracker.options),
                            tracker.ratingMax,
                            tracker.healthMetric?.name,
                            tracker.direction.name,
                            tracker.aggregation.name,
                        ),
                    )
                }
            }
        }
    }
}
