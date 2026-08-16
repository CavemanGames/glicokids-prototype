package com.glicokids.prototype.data.remote

import com.glicokids.prototype.domain.model.FoodProduct
import org.json.JSONObject

/**
 * Module 7 — turns a raw Open Food Facts search response into the [FoodProduct] list the
 * food-search screen renders.
 *
 * A product with no carbohydrate value is dropped: this app exists to count
 * carbohydrates, and a product entry that cannot become a carb count is worse to show than not
 * showing it at all. A product with no `product_name` is dropped for the same reason
 * [OverpassResponseParser] drops an unnamed place — nothing for the child to recognize or tap.
 *
 * Throws [org.json.JSONException] on a response that is not the shape this parses; see
 * [OverpassResponseParser] for why that is this parser's job, not the repository's.
 */
object OpenFoodFactsResponseParser {

    fun parse(json: String): List<FoodProduct> {
        val products = JSONObject(json).optJSONArray("products") ?: return emptyList()

        return buildList {
            for (i in 0 until products.length()) {
                val product = products.getJSONObject(i)

                val carbsPer100g = readCarbsPer100g(product) ?: continue

                val name = product.optString("product_name", "").takeIf { it.isNotBlank() } ?: continue

                add(
                    FoodProduct(
                        name = name,
                        brand = product.optString("brands", "").takeIf { it.isNotBlank() },
                        carbsPer100g = carbsPer100g,
                        servingGrams = parseServingGrams(product)
                    )
                )
            }
        }
    }

    // Open Food Facts moves this value depending on what the caller asked for. Requesting it
    // by name through `fields=carbohydrates_100g` returns it at the top of the product, next to
    // `brands`; asking for the whole `nutriments` block returns it nested inside that object
    // instead. Verified against a live response before this was written — reading only the
    // nested position silently dropped every product, since the flat query never produces a
    // `nutriments` object at all.
    private fun readCarbsPer100g(product: JSONObject): Double? {
        if (product.has("carbohydrates_100g")) return product.optDouble("carbohydrates_100g")
            .takeIf { !it.isNaN() }

        val nutriments = product.optJSONObject("nutriments") ?: return null
        if (!nutriments.has("carbohydrates_100g")) return null
        return nutriments.optDouble("carbohydrates_100g").takeIf { !it.isNaN() }
    }

    // serving_quantity is the numeric grams Open Food Facts derives from the free-text
    // serving_size ("30 g", "1 barra (25g)"...) when it manages to parse it — absent when it
    // could not. Reading only this derived field keeps this parser from having to re-implement
    // OFF's own free-text unit parsing.
    private fun parseServingGrams(product: JSONObject): Double? =
        product.optString("serving_quantity", "").toDoubleOrNull()
}
