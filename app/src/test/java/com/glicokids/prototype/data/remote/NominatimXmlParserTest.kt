package com.glicokids.prototype.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Module 7 — [NominatimXmlParser] against fixtures shaped like the real
 * `reverse?...&format=xml` response, documented at
 * https://nominatim.org/release-docs/latest/api/Reverse/. Robolectric because
 * `XmlPullParserFactory` needs the Android runtime, same reasoning as the JSON parser tests
 * needing it for `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
class NominatimXmlParserTest {

    @Test
    fun `parses the address out of a successful result`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <reversegeocode timestamp="Sun, 16 Aug 26 00:00:00 +0000" attribution="Data (c) OpenStreetMap contributors, ODbL 1.0" querystring="lat=-23.55&amp;lon=-46.63&amp;format=xml">
              <result place_id="123456" osm_type="way" osm_id="789" lat="-23.5610000" lon="-46.6558000" boundingbox="-23.5611,-23.5609,-46.6559,-46.6557">
                Avenida Paulista, Bela Vista, Sao Paulo, Regiao Metropolitana de Sao Paulo, Sao Paulo, Regiao Sudeste, 01310-100, Brasil
              </result>
              <addressparts>
                <road>Avenida Paulista</road>
                <suburb>Bela Vista</suburb>
                <city>Sao Paulo</city>
              </addressparts>
            </reversegeocode>
        """.trimIndent()

        val address = NominatimXmlParser.parseAddress(xml)

        assertThat(address).isEqualTo(
            "Avenida Paulista, Bela Vista, Sao Paulo, Regiao Metropolitana de Sao Paulo, Sao Paulo, Regiao Sudeste, 01310-100, Brasil"
        )
    }

    @Test
    fun `returns null without throwing when the point has no address`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <reversegeocode timestamp="Sun, 16 Aug 26 00:00:00 +0000" attribution="Data (c) OpenStreetMap contributors, ODbL 1.0" querystring="lat=0&amp;lon=0&amp;format=xml">
              <error>Unable to geocode</error>
            </reversegeocode>
        """.trimIndent()

        val address = NominatimXmlParser.parseAddress(xml)

        assertThat(address).isNull()
    }

    @Test
    fun `returns null without throwing on malformed XML`() {
        val truncated = "<reversegeocode><result>Avenida Paulista, Sao Paulo"

        val address = NominatimXmlParser.parseAddress(truncated)

        assertThat(address).isNull()
    }

    @Test
    fun `returns null without throwing on a completely unrelated payload`() {
        val address = NominatimXmlParser.parseAddress("""{"this is": "json, not xml"}""")

        assertThat(address).isNull()
    }
}
