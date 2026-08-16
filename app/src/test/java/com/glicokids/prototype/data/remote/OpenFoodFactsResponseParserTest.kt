package com.glicokids.prototype.data.remote

import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Module 7 — [OpenFoodFactsResponseParser] against fixtures shaped like the real
 * `search.pl?...&json=1&fields=...` response, documented at
 * https://openfoodfacts.github.io/openfoodfacts-server/api/. Robolectric for the same reason as
 * [OverpassResponseParserTest]: `org.json.JSONObject` needs it to not be a stub.
 */
@RunWith(RobolectricTestRunner::class)
class OpenFoodFactsResponseParserTest {

    @Test
    fun `parses a product with brand and serving size`() {
        val json = """
            {
              "count": 1,
              "page": 1,
              "products": [
                {
                  "product_name": "Nutella",
                  "brands": "Ferrero",
                  "nutriments": {"carbohydrates_100g": 57.5, "energy-kcal_100g": 539},
                  "serving_size": "15 g",
                  "serving_quantity": "15"
                }
              ]
            }
        """.trimIndent()

        val products = OpenFoodFactsResponseParser.parse(json)

        assertThat(products).hasSize(1)
        val product = products.single()
        assertThat(product.name).isEqualTo("Nutella")
        assertThat(product.brand).isEqualTo("Ferrero")
        assertThat(product.carbsPer100g).isEqualTo(57.5)
        assertThat(product.servingGrams).isEqualTo(15.0)
    }

    /**
     * Shape taken verbatim from a live response to the query this app actually sends
     * (`fields=product_name,brands,carbohydrates_100g,serving_size`): asking for the carbohydrate
     * field by name returns it at the top of the product, with no `nutriments` object anywhere.
     * Reading only the nested position dropped every single product while the suite stayed green,
     * so this case exists to keep that from coming back.
     */
    @Test
    fun `reads the carbohydrate value when the query returns it at the top of the product`() {
        val json = """
            {
              "count": 2362,
              "page": 1,
              "page_size": 1,
              "products": [
                {
                  "brands": "Cel Sopinho Natural",
                  "carbohydrates_100g": 66,
                  "product_name": "Pao de queijo"
                }
              ]
            }
        """.trimIndent()

        val products = OpenFoodFactsResponseParser.parse(json)

        assertThat(products).hasSize(1)
        assertThat(products.single().carbsPer100g).isEqualTo(66.0)
        assertThat(products.single().brand).isEqualTo("Cel Sopinho Natural")
    }

    @Test
    fun `defaults brand and serving size to null when the fields are absent`() {
        val json = """
            {
              "products": [
                {
                  "product_name": "Generic Rice",
                  "nutriments": {"carbohydrates_100g": 28.0}
                }
              ]
            }
        """.trimIndent()

        val product = OpenFoodFactsResponseParser.parse(json).single()

        assertThat(product.brand).isNull()
        assertThat(product.servingGrams).isNull()
    }

    @Test
    fun `drops a product with no carbohydrates_100g`() {
        val json = """
            {
              "products": [
                {"product_name": "No Nutrition Data", "nutriments": {"energy-kcal_100g": 100}},
                {"product_name": "Has Carbs", "nutriments": {"carbohydrates_100g": 10.0}}
              ]
            }
        """.trimIndent()

        val products = OpenFoodFactsResponseParser.parse(json)

        assertThat(products).hasSize(1)
        assertThat(products.single().name).isEqualTo("Has Carbs")
    }

    @Test
    fun `drops a product with no product_name`() {
        val json = """
            {
              "products": [
                {"nutriments": {"carbohydrates_100g": 10.0}}
              ]
            }
        """.trimIndent()

        val products = OpenFoodFactsResponseParser.parse(json)

        assertThat(products).isEmpty()
    }

    @Test
    fun `returns an empty list when there are no products`() {
        val json = """{"count": 0, "products": []}"""

        val products = OpenFoodFactsResponseParser.parse(json)

        assertThat(products).isEmpty()
    }

    @Test
    fun `returns an empty list when the products field itself is missing`() {
        val json = """{"count": 0}"""

        val products = OpenFoodFactsResponseParser.parse(json)

        assertThat(products).isEmpty()
    }

    @Test
    fun `throws on malformed JSON instead of returning a false empty list`() {
        assertThrows(JSONException::class.java) {
            OpenFoodFactsResponseParser.parse("{not valid json")
        }
    }
}
