package com.glicokids.prototype.domain.model

/**
 * Module 7 — a food product found through Open Food Facts search. [carbsPer100g] is the one
 * field this app cannot do without — a product missing it is dropped by the parser rather than
 * modeled with a nullable field here, so every [FoodProduct] that reaches the UI is already
 * usable with [com.glicokids.prototype.domain.usecase.CalculatePortionCarbsUseCase].
 * [servingGrams] stays nullable: Open Food Facts does not always have a machine-readable
 * serving size, and the child can still type a custom portion when it is missing.
 */
data class FoodProduct(
    val name: String,
    val brand: String?,
    val carbsPer100g: Double,
    val servingGrams: Double?
)
