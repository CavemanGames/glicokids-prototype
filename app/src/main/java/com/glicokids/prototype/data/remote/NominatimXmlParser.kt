package com.glicokids.prototype.data.remote

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Module 7 — the platform `android.location.Geocoder` returns nothing on an emulator without
 * Play Services, and on some real devices too (see
 * [com.glicokids.prototype.data.location.AndroidGeocoder]). This parses the XML
 * `reversegeocode` response from Nominatim (OpenStreetMap) — the fallback for exactly that gap,
 * not decorative XML for the sake of the module's XML requirement.
 *
 * Streaming, forward-only ([XmlPullParser]) rather than a DOM parse: the response is one short
 * address string, there is nothing to gain from building a whole document tree for it.
 *
 * Never throws, on purpose: this feeds
 * [com.glicokids.prototype.domain.repository.GeocodingRepository], whose contract is "neither
 * function ever throws". A response with no result (Nominatim reports that as `<error>`), a
 * missing `<result>` element, and outright malformed XML all degrade to `null` here, same as the
 * platform geocoder finding nothing — there is no separate "the fallback also failed" signal for
 * a caller to handle.
 */
object NominatimXmlParser {

    fun parseAddress(xml: String): String? = try {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())

        var event = parser.eventType
        var address: String? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "result") {
                address = parser.nextText().trim().takeIf { it.isNotBlank() }
                break
            }
            event = parser.next()
        }
        address
    } catch (e: Exception) {
        null
    }
}
