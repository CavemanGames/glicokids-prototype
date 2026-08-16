package com.glicokids.prototype.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.data.model.Contact
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Module 6 — proves that upgrading the database across every schema version lands on
 * the same v4 shape, keeping the user's existing history intact along the way.
 *
 * [GlicoKidsDbHelperTest] always opens the database already at the current version (a
 * fresh Robolectric sandbox has no pre-existing file), so it never exercises `onUpgrade`
 * — that guarantee lived only in a code comment until now. This class hand-builds
 * database files the way a real device at each prior version would have them, then
 * opens them through the current helper.
 *
 * v4 drops `received_messages`: the app is no longer responsible for receiving SMS, so
 * the upgrade path must destroy that table wherever it exists (v3) and never create it
 * again (v1, v2, fresh install).
 */
@RunWith(RobolectricTestRunner::class)
class GlicoKidsDbMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = System.currentTimeMillis()

    @Test
    fun `onUpgrade from v1 to v4 keeps history, adds contacts and never creates received_messages`() {
        seedV1DatabaseWithHistory()

        val dbHelper = GlicoKidsDbHelper(context)
        try {
            val db = dbHelper.writableDatabase // opening a v1 file with a v4 helper drives onUpgrade
            assertThat(db.version).isEqualTo(GlicoKidsDbHelper.DB_VERSION)

            val readings = dbHelper.getGlucoseReadingsSince(0)
            assertThat(readings).hasSize(1)
            assertThat(readings.single().valueMgdl).isEqualTo(215)

            val meals = dbHelper.getRecentMeals(10)
            assertThat(meals).hasSize(1)
            assertThat(meals.single().label).isEqualTo("Jantar migrado")
            assertThat(meals.single().carbsG).isEqualTo(40.0)

            val medals = dbHelper.getMedals()
            assertThat(medals).hasSize(1)
            assertThat(medals.single().code).isEqualTo("medalha_migrada")

            assertThat(dbHelper.getContacts()).isEmpty()

            val contactId = dbHelper.insertContact(
                Contact(
                    name = "Ana",
                    relationship = "Mãe",
                    phone = "11988776543",
                    email = null,
                    receivesAlert = true,
                    receivesReport = false,
                    isPrimary = true,
                    createdAt = now
                )
            )
            assertThat(contactId).isGreaterThan(0)
            assertThat(dbHelper.getContacts().map { it.name }).containsExactly("Ana")

            assertThat(tableExists(db, "received_messages")).isFalse()
        } finally {
            dbHelper.close()
        }
    }

    @Test
    fun `onUpgrade from v2 to v4 keeps history and contacts and does not create received_messages`() {
        seedV2DatabaseWithHistory()

        val dbHelper = GlicoKidsDbHelper(context)
        try {
            val db = dbHelper.writableDatabase // opening a v2 file with a v4 helper drives onUpgrade
            assertThat(db.version).isEqualTo(GlicoKidsDbHelper.DB_VERSION)

            assertThat(dbHelper.getGlucoseReadingsSince(0)).hasSize(1)
            assertThat(dbHelper.getRecentMeals(10)).hasSize(1)
            assertThat(dbHelper.getMedals()).hasSize(1)
            assertThat(dbHelper.getContacts().map { it.name }).containsExactly("Carla")

            assertThat(tableExists(db, "received_messages")).isFalse()
        } finally {
            dbHelper.close()
        }
    }

    /**
     * The destructive step: a real v3 install has `received_messages` with data in it,
     * and the upgrade to v4 must drop it while every other table survives untouched.
     */
    @Test
    fun `onUpgrade from v3 to v4 drops received_messages and keeps history and contacts`() {
        seedV3DatabaseWithHistory()

        val dbHelper = GlicoKidsDbHelper(context)
        try {
            val db = dbHelper.writableDatabase // opening a v3 file with a v4 helper drives onUpgrade
            assertThat(db.version).isEqualTo(GlicoKidsDbHelper.DB_VERSION)

            assertThat(tableExists(db, "received_messages")).isFalse()

            val readings = dbHelper.getGlucoseReadingsSince(0)
            assertThat(readings).hasSize(1)
            assertThat(readings.single().valueMgdl).isEqualTo(88)

            val meals = dbHelper.getRecentMeals(10)
            assertThat(meals).hasSize(1)
            assertThat(meals.single().label).isEqualTo("Café da manhã migrado")

            val medals = dbHelper.getMedals()
            assertThat(medals).hasSize(1)
            assertThat(medals.single().code).isEqualTo("medalha_v3")

            assertThat(dbHelper.getContacts().map { it.name }).containsExactly("Duda")
        } finally {
            dbHelper.close()
        }
    }

    /**
     * A clean install (`onCreate`) must land on the exact same schema as the upgrade
     * chain — a classic hand-written-migration trap is a table that only one of the
     * two paths creates (or, since v4, fails to drop).
     */
    @Test
    fun `a fresh install lands on the same schema as the upgrade chain`() {
        val dbHelper = GlicoKidsDbHelper(context)
        try {
            val db = dbHelper.writableDatabase // no pre-existing file: this drives onCreate
            assertThat(db.version).isEqualTo(GlicoKidsDbHelper.DB_VERSION)
            assertThat(dbHelper.getContacts()).isEmpty()
            assertThat(tableExists(db, "received_messages")).isFalse()

            val contactId = dbHelper.insertContact(
                Contact(
                    name = "Beto",
                    relationship = "Pai",
                    phone = "11999998888",
                    email = null,
                    receivesAlert = true,
                    receivesReport = false,
                    isPrimary = true,
                    createdAt = now
                )
            )
            assertThat(contactId).isGreaterThan(0)
        } finally {
            dbHelper.close()
        }
    }

    /** Can't `COUNT(*)` a table that no longer exists — this checks presence itself. */
    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table)
        ).use { c -> c.moveToFirst() }

    /**
     * Hand-builds a database file matching what a v1 install would have produced: the
     * four original tables (no `contacts`) with `user_version = 1`, plus one row of
     * real data in each table so the migration can be checked for data loss, not just
     * for the presence of the new table.
     */
    private fun seedV1DatabaseWithHistory() {
        val db = context.openOrCreateDatabase(GlicoKidsDbHelper.DB_NAME, Context.MODE_PRIVATE, null)
        try {
            db.execSQL(
                """
                CREATE TABLE glucose_readings (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  value_mgdl INTEGER NOT NULL,
                  status TEXT NOT NULL,
                  source TEXT NOT NULL,
                  created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE meals (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  label TEXT NOT NULL,
                  carbs_g REAL NOT NULL,
                  glucose_mgdl INTEGER,
                  bolus_ui REAL NOT NULL,
                  photo_path TEXT,
                  created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE medals (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  code TEXT NOT NULL UNIQUE,
                  name TEXT NOT NULL,
                  rarity TEXT NOT NULL,
                  unlocked INTEGER NOT NULL DEFAULT 0,
                  unlocked_at INTEGER
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE foods (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  nome TEXT NOT NULL,
                  porcao TEXT NOT NULL,
                  gramas INTEGER NOT NULL,
                  carboidrato_g INTEGER NOT NULL
                );
                """.trimIndent()
            )

            db.insert(
                "glucose_readings", null,
                ContentValues().apply {
                    put("value_mgdl", 215)
                    put("status", "FORA_DA_META")
                    put("source", "MANUAL")
                    put("created_at", now)
                }
            )
            db.insert(
                "meals", null,
                ContentValues().apply {
                    put("label", "Jantar migrado")
                    put("carbs_g", 40.0)
                    put("glucose_mgdl", 130)
                    put("bolus_ui", 3.0)
                    put("created_at", now)
                }
            )
            db.insert(
                "medals", null,
                ContentValues().apply {
                    put("code", "medalha_migrada")
                    put("name", "Medalha Migrada")
                    put("rarity", "OURO")
                    put("unlocked", 1)
                }
            )

            db.version = 1
        } finally {
            db.close()
        }
    }

    /**
     * Hand-builds a database file matching what a v2 install would have produced: the
     * four original tables plus `contacts` (no `received_messages` yet), `user_version = 2`,
     * with one row of real data in each table so the v2->v4 step can be checked for data
     * loss, not just for the absence of `received_messages`.
     */
    private fun seedV2DatabaseWithHistory() {
        val db = context.openOrCreateDatabase(GlicoKidsDbHelper.DB_NAME, Context.MODE_PRIVATE, null)
        try {
            db.execSQL(
                """
                CREATE TABLE glucose_readings (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  value_mgdl INTEGER NOT NULL,
                  status TEXT NOT NULL,
                  source TEXT NOT NULL,
                  created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE meals (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  label TEXT NOT NULL,
                  carbs_g REAL NOT NULL,
                  glucose_mgdl INTEGER,
                  bolus_ui REAL NOT NULL,
                  photo_path TEXT,
                  created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE medals (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  code TEXT NOT NULL UNIQUE,
                  name TEXT NOT NULL,
                  rarity TEXT NOT NULL,
                  unlocked INTEGER NOT NULL DEFAULT 0,
                  unlocked_at INTEGER
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE foods (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  nome TEXT NOT NULL,
                  porcao TEXT NOT NULL,
                  gramas INTEGER NOT NULL,
                  carboidrato_g INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE contacts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  relationship TEXT NOT NULL,
                  phone TEXT NOT NULL,
                  email TEXT,
                  receives_alert INTEGER NOT NULL DEFAULT 1,
                  receives_report INTEGER NOT NULL DEFAULT 0,
                  is_primary INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )

            db.insert(
                "glucose_readings", null,
                ContentValues().apply {
                    put("value_mgdl", 62)
                    put("status", "FORA_DA_META")
                    put("source", "SENSOR")
                    put("created_at", now)
                }
            )
            db.insert(
                "meals", null,
                ContentValues().apply {
                    put("label", "Almoço migrado")
                    put("carbs_g", 55.0)
                    put("glucose_mgdl", 110)
                    put("bolus_ui", 4.0)
                    put("created_at", now)
                }
            )
            db.insert(
                "medals", null,
                ContentValues().apply {
                    put("code", "medalha_v2")
                    put("name", "Medalha da v2")
                    put("rarity", "ESMERALDA")
                    put("unlocked", 1)
                }
            )
            db.insert(
                "contacts", null,
                ContentValues().apply {
                    put("name", "Carla")
                    put("relationship", "Mãe")
                    put("phone", "11966665555")
                    put("receives_alert", 1)
                    put("receives_report", 0)
                    put("is_primary", 1)
                    put("created_at", now)
                }
            )

            db.version = 2
        } finally {
            db.close()
        }
    }

    /**
     * Hand-builds a database file matching what a v3 install would have produced: all
     * six tables, including `received_messages`, with `user_version = 3` and one row of
     * real data in each — `received_messages` too, so the v3->v4 step is proven to
     * actually drop a populated table, not just an empty one.
     */
    private fun seedV3DatabaseWithHistory() {
        val db = context.openOrCreateDatabase(GlicoKidsDbHelper.DB_NAME, Context.MODE_PRIVATE, null)
        try {
            db.execSQL(
                """
                CREATE TABLE glucose_readings (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  value_mgdl INTEGER NOT NULL,
                  status TEXT NOT NULL,
                  source TEXT NOT NULL,
                  created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE meals (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  label TEXT NOT NULL,
                  carbs_g REAL NOT NULL,
                  glucose_mgdl INTEGER,
                  bolus_ui REAL NOT NULL,
                  photo_path TEXT,
                  created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE medals (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  code TEXT NOT NULL UNIQUE,
                  name TEXT NOT NULL,
                  rarity TEXT NOT NULL,
                  unlocked INTEGER NOT NULL DEFAULT 0,
                  unlocked_at INTEGER
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE foods (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  nome TEXT NOT NULL,
                  porcao TEXT NOT NULL,
                  gramas INTEGER NOT NULL,
                  carboidrato_g INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE contacts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  relationship TEXT NOT NULL,
                  phone TEXT NOT NULL,
                  email TEXT,
                  receives_alert INTEGER NOT NULL DEFAULT 1,
                  receives_report INTEGER NOT NULL DEFAULT 0,
                  is_primary INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE received_messages (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  sender_phone TEXT NOT NULL,
                  contact_id INTEGER,
                  body TEXT NOT NULL,
                  received_at INTEGER NOT NULL
                );
                """.trimIndent()
            )

            db.insert(
                "glucose_readings", null,
                ContentValues().apply {
                    put("value_mgdl", 88)
                    put("status", "FORA_DA_META")
                    put("source", "SENSOR")
                    put("created_at", now)
                }
            )
            db.insert(
                "meals", null,
                ContentValues().apply {
                    put("label", "Café da manhã migrado")
                    put("carbs_g", 30.0)
                    put("glucose_mgdl", 95)
                    put("bolus_ui", 2.0)
                    put("created_at", now)
                }
            )
            db.insert(
                "medals", null,
                ContentValues().apply {
                    put("code", "medalha_v3")
                    put("name", "Medalha da v3")
                    put("rarity", "DIAMANTE")
                    put("unlocked", 1)
                }
            )
            db.insert(
                "contacts", null,
                ContentValues().apply {
                    put("name", "Duda")
                    put("relationship", "Avó")
                    put("phone", "11955554444")
                    put("receives_alert", 1)
                    put("receives_report", 0)
                    put("is_primary", 1)
                    put("created_at", now)
                }
            )
            db.insert(
                "received_messages", null,
                ContentValues().apply {
                    put("sender_phone", "11955554444")
                    put("body", "Mensagem da v3, antes da remoção")
                    put("received_at", now)
                }
            )

            db.version = 3
        } finally {
            db.close()
        }
    }
}
