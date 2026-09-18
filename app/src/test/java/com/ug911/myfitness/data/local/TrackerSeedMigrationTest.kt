package com.ug911.myfitness.data.local

import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * The version 1 -> 2 upgrade, exercised against a real SQLite engine on the JVM.
 *
 * The statements here are the same ones the Room migration runs, so the risky part -
 * the SQL - is actually tested rather than assumed. What this does not cover is Room's
 * own plumbing (schema validation, the callback wiring), which needs a device.
 */
class TrackerSeedMigrationTest {

    private lateinit var connection: Connection

    /** The trackers table exactly as version 1 shipped it: no targetValue column. */
    private val v1Schema = """
        CREATE TABLE trackers (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            category TEXT NOT NULL,
            type TEXT NOT NULL,
            unit TEXT,
            active INTEGER NOT NULL,
            sortOrder INTEGER NOT NULL,
            options TEXT NOT NULL,
            ratingMax INTEGER NOT NULL,
            healthMetric TEXT,
            direction TEXT NOT NULL,
            aggregation TEXT NOT NULL
        )
    """.trimIndent()

    /** Just enough of the entries table to prove a rename reaches logged days. */
    private val entriesSchema = """
        CREATE TABLE entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            trackerId INTEGER NOT NULL,
            date TEXT NOT NULL,
            value TEXT NOT NULL,
            notes TEXT,
            source TEXT NOT NULL,
            updatedAtMillis INTEGER NOT NULL
        )
    """.trimIndent()

    private val executor = TrackerSeed.Executor { sql, args ->
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, arg ->
                when (arg) {
                    null -> statement.setNull(index + 1, java.sql.Types.NULL)
                    is Int -> statement.setInt(index + 1, arg)
                    is Long -> statement.setLong(index + 1, arg)
                    is Double -> statement.setDouble(index + 1, arg)
                    else -> statement.setString(index + 1, arg.toString())
                }
            }
            statement.executeUpdate()
        }
    }

    @Before
    fun openDatabase() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    /** An old database, as it exists on a phone that installed the first release. */
    private fun createV1() {
        connection.createStatement().use { it.executeUpdate(v1Schema) }
        connection.createStatement().use { it.executeUpdate(entriesSchema) }
    }

    /** What Room creates on a fresh install today. */
    private fun createV2() {
        createV1()
        connection.createStatement().use { it.executeUpdate("ALTER TABLE trackers ADD COLUMN targetValue REAL") }
    }

    @After
    fun closeDatabase() = connection.close()

    private fun insertLegacyTracker(name: String, category: String, active: Int = 1) {
        connection.prepareStatement(
            "INSERT INTO trackers (name, category, type, unit, active, sortOrder, options, ratingMax, " +
                "healthMetric, direction, aggregation) VALUES (?,?,'BOOLEAN',NULL,?,0,'[]',5,NULL,'UP','DAYS_COMPLETED')",
        ).use {
            it.setString(1, name)
            it.setString(2, category)
            it.setInt(3, active)
            it.executeUpdate()
        }
    }

    /** Exactly what MIGRATION_1_2 does, in the same order. */
    private fun applyMigration() {
        connection.createStatement().use { it.executeUpdate("ALTER TABLE trackers ADD COLUMN targetValue REAL") }
        TrackerSeed.migrateToPersonalDay(executor)
    }

    private fun query(sql: String): List<Map<String, String?>> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                val columns = (1..rows.metaData.columnCount).map { rows.metaData.getColumnLabel(it) }
                buildList {
                    while (rows.next()) {
                        add(columns.associateWith { rows.getString(it) })
                    }
                }
            }
        }

    @Test
    fun `fresh install seeds the personal day once`() {
        createV2()

        TrackerSeed.seedFresh(executor)

        val names = query("SELECT name FROM trackers").map { it["name"] }
        assertEquals(PersonalDay.trackers().size, names.size)
        assertTrue("Woke up" in names)
        assertTrue("Exercises" in names)
        assertTrue("Slept at" in names)
        // Running it twice must not duplicate rows.
        TrackerSeed.seedFresh(executor)
        assertEquals(PersonalDay.trackers().size, query("SELECT name FROM trackers").size)
    }

    @Test
    fun `the gym checklist keeps all eleven exercises in order`() {
        createV2()
        TrackerSeed.seedFresh(executor)

        val row = query("SELECT options, type FROM trackers WHERE name = 'Exercises'").single()
        assertEquals(TrackerType.MULTI_SELECT.name, row["type"])
        assertEquals(Converters().listToString(PersonalDay.EXERCISES), row["options"])
        val storedOptions = Converters().stringToList(row["options"]!!)
        assertEquals(11, storedOptions.size)
        assertEquals("Shoulders", storedOptions.first())
        assertEquals("Jumping jacks", storedOptions.last())
    }

    @Test
    fun `upgrading remaps old categories onto sections`() {
        createV1()
        insertLegacyTracker("Meditation", "BEHAVIOUR")
        insertLegacyTracker("Junk food", "NUTRITION")

        applyMigration()

        val meditation = query("SELECT category FROM trackers WHERE name = 'Meditation'").single()
        val junk = query("SELECT category FROM trackers WHERE name = 'Junk food'").single()
        assertEquals(DaySection.NIGHT.name, meditation["category"])
        assertEquals(DaySection.BREAKFAST.name, junk["category"])
    }

    @Test
    fun `upgrading retires old trackers without deleting them`() {
        createV1()
        insertLegacyTracker("Meditation", "BEHAVIOUR")

        applyMigration()

        val meditation = query("SELECT active FROM trackers WHERE name = 'Meditation'").single()
        assertEquals("0", meditation["active"])
        // Still present, so any entries logged against it remain readable.
        assertEquals(1, query("SELECT id FROM trackers WHERE name = 'Meditation'").size)
    }

    @Test
    fun `a tracker that exists in both sets keeps its row and its history`() {
        createV1()
        insertLegacyTracker("Weight", "HEALTH")
        val originalId = query("SELECT id FROM trackers WHERE name = 'Weight'").single()["id"]

        applyMigration()

        val rows = query("SELECT id, category, type, active, unit FROM trackers WHERE name = 'Weight'")
        assertEquals(1, rows.size)
        val weight = rows.single()
        // Same row id, so entries pointing at it still resolve.
        assertEquals(originalId, weight["id"])
        assertEquals(DaySection.BODY.name, weight["category"])
        assertEquals(TrackerType.NUMBER.name, weight["type"])
        assertEquals("kg", weight["unit"])
        assertEquals("1", weight["active"])
    }

    @Test
    fun `upgrading adds the new day-shaped trackers`() {
        createV1()
        insertLegacyTracker("Meditation", "BEHAVIOUR")

        applyMigration()

        val active = query("SELECT name FROM trackers WHERE active = 1").mapNotNull { it["name"] }
        assertTrue("Woke up" in active)
        assertTrue("Dropped Vihaan at school" in active)
        assertTrue("Water at office" in active)
        assertTrue("Played with Vihaan" in active)
        assertFalse("Meditation" in active)
    }

    @Test
    fun `standing targets survive the upgrade`() {
        createV1()
        applyMigration()

        val wake = query("SELECT targetValue, direction FROM trackers WHERE name = 'Woke up'").single()
        assertEquals(300.0, wake["targetValue"]!!.toDouble(), 0.001)
        assertEquals("DOWN", wake["direction"])

        val water = query("SELECT targetValue FROM trackers WHERE name = 'Water at office'").single()
        assertEquals(8.0, water["targetValue"]!!.toDouble(), 0.001)
    }
}

/**
 * Version 3: the corrections that came out of using the app - the breakfast item is
 * Poha, the school run does not need its own times, and lunch and dinner get their own
 * food lists.
 */
class SecondMigrationTest {

    private lateinit var connection: Connection

    private val executor = TrackerSeed.Executor { sql, args ->
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, arg ->
                when (arg) {
                    null -> statement.setNull(index + 1, java.sql.Types.NULL)
                    is Int -> statement.setInt(index + 1, arg)
                    is Long -> statement.setLong(index + 1, arg)
                    is Double -> statement.setDouble(index + 1, arg)
                    else -> statement.setString(index + 1, arg.toString())
                }
            }
            statement.executeUpdate()
        }
    }

    @Before
    fun openDatabase() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use {
            it.executeUpdate(
                "CREATE TABLE trackers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                    "category TEXT NOT NULL, type TEXT NOT NULL, unit TEXT, active INTEGER NOT NULL, " +
                    "sortOrder INTEGER NOT NULL, options TEXT NOT NULL, ratingMax INTEGER NOT NULL, " +
                    "healthMetric TEXT, direction TEXT NOT NULL, aggregation TEXT NOT NULL, targetValue REAL)",
            )
        }
        connection.createStatement().use {
            it.executeUpdate(
                "CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, trackerId INTEGER NOT NULL, " +
                    "date TEXT NOT NULL, value TEXT NOT NULL, notes TEXT, source TEXT NOT NULL, " +
                    "updatedAtMillis INTEGER NOT NULL)",
            )
        }
        // A version 2 database: the day-shaped set, including the two school-run times.
        TrackerSeed.upsert(
            executor,
            listOf(
                Tracker(
                    name = "Breakfast",
                    section = DaySection.BREAKFAST,
                    type = TrackerType.MULTI_SELECT,
                    options = listOf("Chicken", "Eggs", "Dosa", "Bohara"),
                ),
                Tracker(name = "Left for school", section = DaySection.SCHOOL_RUN, type = TrackerType.TIME),
                Tracker(name = "Back from school", section = DaySection.SCHOOL_RUN, type = TrackerType.TIME),
                Tracker(name = "Dropped Vihaan at school", section = DaySection.SCHOOL_RUN, type = TrackerType.BOOLEAN),
            ),
        )
    }

    @After
    fun closeDatabase() = connection.close()

    private fun applyMigration() {
        TrackerSeed.renameOption(executor, from = "Bohara", to = "Poha")
        TrackerSeed.retire(executor, "Left for school")
        TrackerSeed.retire(executor, "Back from school")
        TrackerSeed.insertMissing(executor, PersonalDay.trackers())
    }

    private fun query(sql: String): List<Map<String, String?>> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                val columns = (1..rows.metaData.columnCount).map { rows.metaData.getColumnLabel(it) }
                buildList {
                    while (rows.next()) add(columns.associateWith { rows.getString(it) })
                }
            }
        }

    private fun logBreakfast(vararg items: String) {
        val trackerId = query("SELECT id FROM trackers WHERE name = 'Breakfast'").single()["id"]!!.toLong()
        connection.prepareStatement(
            "INSERT INTO entries (trackerId, date, value, notes, source, updatedAtMillis) " +
                "VALUES (?, '2026-09-16', ?, NULL, 'MANUAL', 0)",
        ).use {
            it.setLong(1, trackerId)
            it.setString(2, TrackerValue.Choices(items.toList()).encode())
            it.executeUpdate()
        }
    }

    @Test
    fun `the breakfast item becomes Poha`() {
        applyMigration()

        val options = Converters().stringToList(
            query("SELECT options FROM trackers WHERE name = 'Breakfast'").single()["options"]!!,
        )
        assertTrue("Poha" in options)
        assertFalse("Bohara" in options)
        // The other items are untouched.
        assertTrue("Dosa" in options)
        assertEquals(4, options.size)
    }

    @Test
    fun `a day already logged as Bohara reads back as Poha`() {
        logBreakfast("Eggs", "Bohara")

        applyMigration()

        val stored = query("SELECT value FROM entries").single()["value"]!!
        val decoded = TrackerValue.decode(TrackerType.MULTI_SELECT, stored) as TrackerValue.Choices
        assertEquals(listOf("Eggs", "Poha"), decoded.selected)
    }

    @Test
    fun `the school run keeps only the drop`() {
        applyMigration()

        val schoolRun = query("SELECT name, active FROM trackers WHERE category = 'SCHOOL_RUN'")
        val active = schoolRun.filter { it["active"] == "1" }.mapNotNull { it["name"] }
        assertEquals(listOf("Dropped Vihaan at school"), active)
        // Retired, not deleted: three rows are still there.
        assertEquals(3, schoolRun.size)
    }

    @Test
    fun `lunch and dinner food lists arrive`() {
        applyMigration()

        val lunch = query("SELECT options, category FROM trackers WHERE name = 'Lunch'").single()
        assertEquals(DaySection.OFFICE.name, lunch["category"])
        assertTrue("Roti" in Converters().stringToList(lunch["options"]!!))

        val dinner = query("SELECT category FROM trackers WHERE name = 'Dinner food'").single()
        assertEquals(DaySection.EVENING.name, dinner["category"])
    }

    @Test
    fun `adding trackers does not switch a retired one back on`() {
        // Someone switched Dinner off by hand before upgrading.
        connection.createStatement().use {
            it.executeUpdate("INSERT INTO trackers (name, category, type, unit, active, sortOrder, options, " +
                "ratingMax, healthMetric, direction, aggregation, targetValue) VALUES ('Dinner','EVENING'," +
                "'BOOLEAN',NULL,0,1,'[]',5,NULL,'UP','DAYS_COMPLETED',NULL)")
        }

        applyMigration()

        assertEquals("0", query("SELECT active FROM trackers WHERE name = 'Dinner'").single()["active"])
    }
}
