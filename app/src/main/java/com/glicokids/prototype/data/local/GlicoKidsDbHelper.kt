package com.glicokids.prototype.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.glicokids.prototype.R
import com.glicokids.prototype.data.model.Food
import com.glicokids.prototype.data.model.GlucoseReading
import com.glicokids.prototype.data.model.MealEntry
import com.glicokids.prototype.data.model.MedalRecord
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Módulo 5 — requisito 7: banco local via [SQLiteOpenHelper] escrito à mão.
 *
 * Room é PROIBIDO neste projeto (requisito acadêmico) e suas dependências
 * foram removidas do build. Schema conforme handoff §8.2.
 *
 * Toda chamada aqui toca disco: use fora da main thread.
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

        seedFoods(db)
        seedMedals(db)
    }

    /** Protótipo: recriar é aceitável, não há dado de produção a preservar. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        listOf("glucose_readings", "meals", "medals", "foods").forEach {
            db.execSQL("DROP TABLE IF EXISTS $it")
        }
        onCreate(db)
    }

    // ------------------------------------------------------------------
    // Semeadura
    // ------------------------------------------------------------------

    /**
     * Módulo 5 — requisito 6: `openRawResource` lendo `res/raw/alimentos.json`.
     * Sem isso a tabela de carboidratos nasce vazia.
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

    /** As 6 medalhas do design (b12): 4 desbloqueadas, 2 bloqueadas. */
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
    // Escrita
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

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    /** Leituras a partir de [sinceMillis], da mais antiga para a mais recente. */
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
                    source = runCatching { GlucoseReading.Source.valueOf(c.getString(3)) }
                        .getOrDefault(GlucoseReading.Source.MANUAL),
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
        const val DB_VERSION = 1
    }
}
