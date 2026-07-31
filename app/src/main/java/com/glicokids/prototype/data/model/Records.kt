package com.glicokids.prototype.data.model

import com.glicokids.prototype.util.UIHelper

/** Uma leitura de glicemia gravada na tabela `glucose_readings`. */
data class GlucoseReading(
    val id: Long = 0,
    val valueMgdl: Int,
    val status: UIHelper.GlucoseStatus,
    val source: Source,
    val createdAt: Long
) {
    enum class Source { MANUAL, SENSOR }
}

/** Uma refeição registrada na Missão da Refeição (tabela `meals`). */
data class MealEntry(
    val id: Long = 0,
    val label: String,
    val carbsG: Double,
    val glucoseMgdl: Int?,
    val bolusUi: Double,
    val photoPath: String?,
    val createdAt: Long
)

/** Uma medalha da galeria (tabela `medals`). */
data class MedalRecord(
    val id: Long = 0,
    val code: String,
    val name: String,
    val rarity: String,
    val unlocked: Boolean,
    val unlockedAt: Long?
)

/** Um alimento da tabela de carboidratos (tabela `foods`, semeada de `raw/alimentos.json`). */
data class Food(
    val id: Long = 0,
    val nome: String,
    val porcao: String,
    val gramas: Int,
    val carboidratoG: Int
)
