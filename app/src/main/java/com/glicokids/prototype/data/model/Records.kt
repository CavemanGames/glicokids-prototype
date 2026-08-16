package com.glicokids.prototype.data.model

import com.glicokids.prototype.domain.model.ReadingSource
import com.glicokids.prototype.util.UIHelper

/** A glucose reading stored in the `glucose_readings` table. */
data class GlucoseReading(
    val id: Long = 0,
    val valueMgdl: Int,
    val status: UIHelper.GlucoseStatus,
    val source: ReadingSource,
    val createdAt: Long
)

/** A meal logged through the Meal Mission (`meals` table). */
data class MealEntry(
    val id: Long = 0,
    val label: String,
    val carbsG: Double,
    val glucoseMgdl: Int?,
    val bolusUi: Double,
    val photoPath: String?,
    val createdAt: Long
)

/** A gallery medal (`medals` table). */
data class MedalRecord(
    val id: Long = 0,
    val code: String,
    val name: String,
    val rarity: String,
    val unlocked: Boolean,
    val unlockedAt: Long?
)

/** A food from the carbohydrate table (`foods`, seeded from `raw/alimentos.json`). */
data class Food(
    val id: Long = 0,
    val nome: String,
    val porcao: String,
    val gramas: Int,
    val carboidratoG: Int
)

/** A support network member — each person has independent alert/report permissions (`contacts` table, schema v2). */
data class Contact(
    val id: Long = 0,
    val name: String,
    val relationship: String,
    val phone: String,
    val email: String?,
    val receivesAlert: Boolean,
    val receivesReport: Boolean,
    val isPrimary: Boolean,
    val createdAt: Long
)
