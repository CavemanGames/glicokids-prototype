package com.glicokids.prototype.data.remote

import com.glicokids.prototype.domain.model.HealthPlaceType
import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Module 7 — [OverpassResponseParser] against fixtures shaped like the real Overpass API JSON
 * output (`out center` / node query), documented at
 * https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_API_by_Example. Robolectric is
 * needed here for the same reason it is in `GlicoKidsDbHelperTest`: `org.json.JSONObject` is
 * stubbed to throw on a plain JVM unit test and only works under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class OverpassResponseParserTest {

    @Test
    fun `parses named elements and sorts them nearest first`() {
        val json = """
            {
              "version": 0.6,
              "generator": "Overpass API 0.7.62",
              "elements": [
                {"type":"node","id":1,"lat":0.01,"lon":0.01,"tags":{"amenity":"pharmacy","name":"Farmacia Popular"}},
                {"type":"node","id":2,"lat":0.0,"lon":0.001,"tags":{"amenity":"hospital","name":"Hospital Municipal"}}
              ]
            }
        """.trimIndent()

        val places = OverpassResponseParser.parse(json, originLat = 0.0, originLng = 0.0)

        assertThat(places).hasSize(2)
        assertThat(places.map { it.name }).containsExactly("Hospital Municipal", "Farmacia Popular").inOrder()
        assertThat(places[0].distanceMeters).isLessThan(places[1].distanceMeters)
        assertThat(places[0].type).isEqualTo(HealthPlaceType.HOSPITAL)
        assertThat(places[1].type).isEqualTo(HealthPlaceType.PHARMACY)
    }

    @Test
    fun `maps the doctors amenity to DOCTOR and an unrecognized amenity to UNKNOWN`() {
        val json = """
            {
              "elements": [
                {"type":"node","id":1,"lat":0.0,"lon":0.0,"tags":{"amenity":"doctors","name":"Dr. Silva"}},
                {"type":"node","id":2,"lat":0.0,"lon":0.0,"tags":{"amenity":"veterinary","name":"Pet Clinic"}}
              ]
            }
        """.trimIndent()

        val places = OverpassResponseParser.parse(json, originLat = 0.0, originLng = 0.0)

        assertThat(places.first { it.name == "Dr. Silva" }.type).isEqualTo(HealthPlaceType.DOCTOR)
        assertThat(places.first { it.name == "Pet Clinic" }.type).isEqualTo(HealthPlaceType.UNKNOWN)
    }

    @Test
    fun `drops elements with no name and elements missing lat or lon`() {
        val json = """
            {
              "elements": [
                {"type":"node","id":1,"lat":0.0,"lon":0.0,"tags":{"amenity":"parking"}},
                {"type":"node","id":2,"lat":0.0,"lon":0.0,"tags":{"amenity":"clinic","name":""}},
                {"type":"node","id":3,"tags":{"amenity":"hospital","name":"No Coordinates Hospital"}},
                {"type":"node","id":4,"lat":0.0,"lon":0.0,"tags":{"amenity":"hospital","name":"Real Hospital"}}
              ]
            }
        """.trimIndent()

        val places = OverpassResponseParser.parse(json, originLat = 0.0, originLng = 0.0)

        assertThat(places).hasSize(1)
        assertThat(places.single().name).isEqualTo("Real Hospital")
    }

    @Test
    fun `returns an empty list when there are no elements`() {
        val json = """{"version": 0.6, "elements": []}"""

        val places = OverpassResponseParser.parse(json, originLat = 0.0, originLng = 0.0)

        assertThat(places).isEmpty()
    }

    @Test
    fun `returns an empty list when the elements field itself is missing`() {
        val json = """{"version": 0.6}"""

        val places = OverpassResponseParser.parse(json, originLat = 0.0, originLng = 0.0)

        assertThat(places).isEmpty()
    }

    @Test
    fun `throws on malformed JSON instead of returning a false empty list`() {
        assertThrows(JSONException::class.java) {
            OverpassResponseParser.parse("{not valid json", originLat = 0.0, originLng = 0.0)
        }
    }
}
