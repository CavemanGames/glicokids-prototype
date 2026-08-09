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
 * Module 6 — proves that upgrading the database from schema v1 to v2 keeps the
 * user's existing history and only adds the `contacts` table.
 *
 * [GlicoKidsDbHelperTest] always opens the database already at v2 (a fresh Robolectric
 * sandbox has no pre-existing file), so it never exercises `onUpgrade` — that guarantee
 * lived only in a code comment until now. This class hand-builds a v1 database file the
 * way a real device would have it, then opens it through the current (v2) helper.
 */
@RunWith(RobolectricTestRunner::class)
class GlicoKidsDbMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = System.currentTimeMillis()

    @Test
    fun `onUpgrade keeps existing readings, meals and medals and adds an empty contacts table`() {
        seedV1DatabaseWithHistory()

        val dbHelper = GlicoKidsDbHelper(context)
        try {
            val db = dbHelper.writableDatabase // opening a v1 file with a v2 helper drives onUpgrade
            assertThat(db.version).isEqualTo(2)

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
        } finally {
            dbHelper.close()
        }
    }

    /**
     * A clean install (`onCreate`) must land on the exact same schema as an upgraded
     * database (`onUpgrade`) — a classic hand-written-migration trap is a `contacts`
     * table that only one of the two paths creates.
     */
    @Test
    fun `a fresh install also creates the contacts table`() {
        val dbHelper = GlicoKidsDbHelper(context)
        try {
            val db = dbHelper.writableDatabase // no pre-existing file: this drives onCreate
            assertThat(db.version).isEqualTo(2)
            assertThat(dbHelper.getContacts()).isEmpty()

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
}
