package com.glicokids.prototype.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.glicokids.prototype.R
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.data.model.Food
import com.glicokids.prototype.data.model.GlucoseReading
import com.glicokids.prototype.data.model.MealEntry
import com.glicokids.prototype.data.model.MedalRecord
import com.glicokids.prototype.data.model.ReceivedMessage
import com.glicokids.prototype.domain.model.ReadingSource
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module 5 — requirement 7: local database through a hand-written [SQLiteOpenHelper].
 *
 * Room is FORBIDDEN in this project (academic requirement) and its dependencies
 * were removed from the build. Every table below is created and migrated by hand,
 * with a single well-defined owner for each one — no ORM-generated schema.
 *
 * Every call here touches disk: use it off the main thread.
 */
@Singleton
class GlicoKidsDbHelper @Inject constructor(
    @ApplicationContext private val context: Context
) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
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
        createContactsTable(db)
        createReceivedMessagesTable(db)

        seedFoods(db)
        seedMedals(db)
    }

    /**
     * Module 6 — schema v2 adds `contacts`, schema v3 adds `received_messages`. Each
     * step only adds the table it owns and never touches the ones before it — glucose
     * history, meals, medals, foods and contacts must survive every upgrade, so no
     * `DROP TABLE` here. A jump straight from v1 to v3 must apply both steps in the
     * same pass, which is why each is guarded by its own `if`, not an `else`.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createContactsTable(db)
        }
        if (oldVersion < 3) {
            createReceivedMessagesTable(db)
        }
    }

    /** Shared by [onCreate] and [onUpgrade] so the schema is defined in a single place. */
    private fun createContactsTable(db: SQLiteDatabase) {
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
    }

    /**
     * Module 6 — schema v3: the incoming-message inbox behind requirement 3 (b23). Shared
     * by [onCreate] and [onUpgrade], same as [createContactsTable]. `contact_id` is left
     * nullable on purpose — an unknown sender still gets its message stored and shown.
     */
    private fun createReceivedMessagesTable(db: SQLiteDatabase) {
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
    }

    // ------------------------------------------------------------------
    // Seeding
    // ------------------------------------------------------------------

    /**
     * Module 5 — requirement 6: `openRawResource` reading `res/raw/alimentos.json`.
     * Without it the carbohydrate table starts empty.
     */
    private fun seedFoods(db: SQLiteDatabase) {
        val json = context.resources.openRawResource(R.raw.alimentos).use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { it.readText() }
        }
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            db.insert(
                "foods", null,
                ContentValues().apply {
                    put("nome", item.getString("nome"))
                    put("porcao", item.getString("porcao"))
                    put("gramas", item.getInt("gramas"))
                    put("carboidrato_g", item.getInt("carboidrato_g"))
                }
            )
        }
    }

    /** The 6 medals from the design (b12): 4 unlocked, 2 locked. */
    private fun seedMedals(db: SQLiteDatabase) {
        val seed = listOf(
            arrayOf("primeira_missao", "Primeira Missão", "OURO", 1),
            arrayOf("semana_na_meta", "Semana na Meta", "ESMERALDA", 1),
            arrayOf("corredor_cosmico", "Corredor Cósmico", "DIAMANTE", 1),
            arrayOf("dez_fotos_prato", "10 Fotos de Prato", "OURO", 1),
            arrayOf("maratonista", "Maratonista", "ESMERALDA", 0),
            arrayOf("mes_perfeito", "Mês Perfeito", "DIAMANTE", 0)
        )
        seed.forEach { (code, name, rarity, unlocked) ->
            db.insert(
                "medals", null,
                ContentValues().apply {
                    put("code", code as String)
                    put("name", name as String)
                    put("rarity", rarity as String)
                    put("unlocked", unlocked as Int)
                }
            )
        }
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    fun insertGlucoseReading(reading: GlucoseReading): Long =
        writableDatabase.insert(
            "glucose_readings", null,
            ContentValues().apply {
                put("value_mgdl", reading.valueMgdl)
                put("status", reading.status.name)
                put("source", reading.source.name)
                put("created_at", reading.createdAt)
            }
        )

    fun insertMeal(meal: MealEntry): Long =
        writableDatabase.insert(
            "meals", null,
            ContentValues().apply {
                put("label", meal.label)
                put("carbs_g", meal.carbsG)
                put("glucose_mgdl", meal.glucoseMgdl)
                put("bolus_ui", meal.bolusUi)
                put("photo_path", meal.photoPath)
                put("created_at", meal.createdAt)
            }
        )

    fun insertContact(contact: Contact): Long =
        writableDatabase.insert("contacts", null, contact.toContentValues())

    fun updateContact(contact: Contact) {
        writableDatabase.update(
            "contacts",
            contact.toContentValues(),
            "id = ?",
            arrayOf(contact.id.toString())
        )
    }

    /** Silently refuses when [id] belongs to the primary contact — that row is never removed. */
    fun deleteContact(id: Long) {
        writableDatabase.delete("contacts", "id = ? AND is_primary = 0", arrayOf(id.toString()))
    }

    private fun Contact.toContentValues() = ContentValues().apply {
        put("name", name)
        put("relationship", relationship)
        put("phone", phone)
        put("email", email)
        put("receives_alert", if (receivesAlert) 1 else 0)
        put("receives_report", if (receivesReport) 1 else 0)
        put("is_primary", if (isPrimary) 1 else 0)
        put("created_at", createdAt)
    }

    /** Module 6 — b23: stores one inbound message. [SmsReceiver] is the only caller. */
    fun insertReceivedMessage(message: ReceivedMessage): Long =
        writableDatabase.insert("received_messages", null, message.toContentValues())

    private fun ReceivedMessage.toContentValues() = ContentValues().apply {
        put("sender_phone", senderPhone)
        if (contactId != null) put("contact_id", contactId) else putNull("contact_id")
        put("body", body)
        put("received_at", receivedAt)
    }

    /**
     * Module 6 — guarantees a primary contact row exists even though the guardian
     * onboarding screens (b3/f3) are not implemented yet (Phase 4 of the roadmap).
     * Idempotent — does nothing when a primary contact already exists.
     *
     * TODO(onboarding): AppPreferences currently holds no guardian identity data (only the
     * child's profile — see AppPreferences.kt), so [name], [relationship] and [phone] are
     * received as parameters instead of being read here. Once b3/f3 collect them, call this
     * from the onboarding completion flow with the values gathered there.
     */
    fun ensurePrimaryContact(name: String, relationship: String, phone: String, email: String?, createdAt: Long): Long? {
        val alreadyHasPrimary = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM contacts WHERE is_primary = 1",
            null
        ).use { c -> c.moveToFirst(); c.getInt(0) > 0 }
        if (alreadyHasPrimary) return null

        return insertContact(
            Contact(
                name = name,
                relationship = relationship,
                phone = phone,
                email = email,
                receivesAlert = true,
                receivesReport = true,
                isPrimary = true,
                createdAt = createdAt
            )
        )
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /** Readings from [sinceMillis] onwards, oldest first. */
    fun getGlucoseReadingsSince(sinceMillis: Long): List<GlucoseReading> {
        val out = mutableListOf<GlucoseReading>()
        readableDatabase.rawQuery(
            "SELECT id, value_mgdl, status, source, created_at FROM glucose_readings " +
                "WHERE created_at >= ? ORDER BY created_at ASC",
            arrayOf(sinceMillis.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += GlucoseReading(
                    id = c.getLong(0),
                    valueMgdl = c.getInt(1),
                    status = runCatching { UIHelper.GlucoseStatus.valueOf(c.getString(2)) }
                        .getOrDefault(UIHelper.GlucoseStatus.NA_META),
                    source = runCatching { ReadingSource.valueOf(c.getString(3)) }
                        .getOrDefault(ReadingSource.MANUAL),
                    createdAt = c.getLong(4)
                )
            }
        }
        return out
    }

    fun getMealsSince(sinceMillis: Long): List<MealEntry> =
        queryMeals("WHERE created_at >= ? ORDER BY created_at ASC", arrayOf(sinceMillis.toString()))

    fun getRecentMeals(limit: Int): List<MealEntry> =
        queryMeals("ORDER BY created_at DESC LIMIT ?", arrayOf(limit.toString()))

    private fun queryMeals(clause: String, args: Array<String>): List<MealEntry> {
        val out = mutableListOf<MealEntry>()
        readableDatabase.rawQuery(
            "SELECT id, label, carbs_g, glucose_mgdl, bolus_ui, photo_path, created_at FROM meals $clause",
            args
        ).use { c ->
            while (c.moveToNext()) {
                out += MealEntry(
                    id = c.getLong(0),
                    label = c.getString(1),
                    carbsG = c.getDouble(2),
                    glucoseMgdl = if (c.isNull(3)) null else c.getInt(3),
                    bolusUi = c.getDouble(4),
                    photoPath = if (c.isNull(5)) null else c.getString(5),
                    createdAt = c.getLong(6)
                )
            }
        }
        return out
    }

    fun getMedals(): List<MedalRecord> {
        val out = mutableListOf<MedalRecord>()
        readableDatabase.rawQuery(
            "SELECT id, code, name, rarity, unlocked, unlocked_at FROM medals ORDER BY id ASC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += MedalRecord(
                    id = c.getLong(0),
                    code = c.getString(1),
                    name = c.getString(2),
                    rarity = c.getString(3),
                    unlocked = c.getInt(4) == 1,
                    unlockedAt = if (c.isNull(5)) null else c.getLong(5)
                )
            }
        }
        return out
    }

    fun getContacts(): List<Contact> = queryContacts("ORDER BY is_primary DESC, id ASC", null)

    /** Contacts subscribed to the SMS/notification alert (`receives_alert = 1`). */
    fun getAlertRecipients(): List<Contact> =
        queryContacts("WHERE receives_alert = 1 ORDER BY id ASC", null)

    /** Contacts subscribed to the e-mail report — needs a non-empty e-mail too. */
    fun getReportRecipients(): List<Contact> =
        queryContacts(
            "WHERE receives_report = 1 AND email IS NOT NULL AND TRIM(email) != '' ORDER BY id ASC",
            null
        )

    /**
     * Matches [phone] against every stored contact by digits only — parentheses, spaces,
     * hyphens and the `+55` country code are ignored on both sides of the comparison.
     */
    fun findContactByPhone(phone: String): Contact? {
        val target = normalizePhone(phone)
        return getContacts().firstOrNull { normalizePhone(it.phone) == target }
    }

    private fun normalizePhone(phone: String): String {
        val digitsOnly = phone.filter { it.isDigit() }
        return if (phone.trim().startsWith("+55")) digitsOnly.removePrefix("55") else digitsOnly
    }

    /** Every received message, most recent first — b23's inbox. */
    fun getReceivedMessages(): List<ReceivedMessage> {
        val out = mutableListOf<ReceivedMessage>()
        readableDatabase.rawQuery(
            "SELECT id, sender_phone, contact_id, body, received_at FROM received_messages " +
                "ORDER BY received_at DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += ReceivedMessage(
                    id = c.getLong(0),
                    senderPhone = c.getString(1),
                    contactId = if (c.isNull(2)) null else c.getLong(2),
                    body = c.getString(3),
                    receivedAt = c.getLong(4)
                )
            }
        }
        return out
    }

    /** Backs the count chip on b19's "Transmissões recebidas" row. */
    fun getReceivedMessageCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM received_messages", null).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private fun queryContacts(clause: String, args: Array<String>?): List<Contact> {
        val out = mutableListOf<Contact>()
        readableDatabase.rawQuery(
            "SELECT id, name, relationship, phone, email, receives_alert, receives_report, " +
                "is_primary, created_at FROM contacts $clause",
            args
        ).use { c ->
            while (c.moveToNext()) {
                out += Contact(
                    id = c.getLong(0),
                    name = c.getString(1),
                    relationship = c.getString(2),
                    phone = c.getString(3),
                    email = if (c.isNull(4)) null else c.getString(4),
                    receivesAlert = c.getInt(5) == 1,
                    receivesReport = c.getInt(6) == 1,
                    isPrimary = c.getInt(7) == 1,
                    createdAt = c.getLong(8)
                )
            }
        }
        return out
    }

    /** Most recent glucose reading, or null when none was ever recorded. */
    fun getLastGlucoseReading(): GlucoseReading? {
        readableDatabase.rawQuery(
            "SELECT id, value_mgdl, status, source, created_at FROM glucose_readings " +
                "ORDER BY created_at DESC LIMIT 1",
            null
        ).use { c ->
            if (!c.moveToFirst()) return null
            return GlucoseReading(
                id = c.getLong(0),
                valueMgdl = c.getInt(1),
                status = runCatching { UIHelper.GlucoseStatus.valueOf(c.getString(2)) }
                    .getOrDefault(UIHelper.GlucoseStatus.NA_META),
                source = runCatching { ReadingSource.valueOf(c.getString(3)) }
                    .getOrDefault(ReadingSource.MANUAL),
                createdAt = c.getLong(4)
            )
        }
    }

    fun getFoods(): List<Food> {
        val out = mutableListOf<Food>()
        readableDatabase.rawQuery(
            "SELECT id, nome, porcao, gramas, carboidrato_g FROM foods ORDER BY nome ASC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += Food(
                    id = c.getLong(0),
                    nome = c.getString(1),
                    porcao = c.getString(2),
                    gramas = c.getInt(3),
                    carboidratoG = c.getInt(4)
                )
            }
        }
        return out
    }

    companion object {
        const val DB_NAME = "glicokids.db"
        const val DB_VERSION = 3
    }
}
