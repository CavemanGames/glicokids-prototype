package com.glicokids.prototype.presentation.parents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Requirement 7 — [AlertMapActivity.formatDistance] is the one piece of the nearby-help screen
 * that does not touch [com.google.android.gms.maps.GoogleMap] or any other Android API, so it
 * is the one piece of that screen this suite can exercise directly: Robolectric has no shadow
 * for `GoogleMap` itself, and [AlertMapActivity] never launches through `ActivityScenario`
 * because of that (see the class kdoc).
 */
class AlertMapActivityFormatDistanceTest {

    @Test
    fun `distance under a kilometer is rendered as whole meters`() {
        assertThat(AlertMapActivity.formatDistance(320.0)).isEqualTo("320 m")
    }

    @Test
    fun `distance is rounded to the nearest whole meter`() {
        assertThat(AlertMapActivity.formatDistance(319.6)).isEqualTo("320 m")
    }

    @Test
    fun `distance right at a kilometer switches to kilometers`() {
        assertThat(AlertMapActivity.formatDistance(1000.0)).isEqualTo("1,0 km")
    }

    @Test
    fun `distance above a kilometer is rendered with one decimal in pt-BR`() {
        assertThat(AlertMapActivity.formatDistance(2350.0)).isEqualTo("2,4 km")
    }

    @Test
    fun `zero distance is rendered as zero meters`() {
        assertThat(AlertMapActivity.formatDistance(0.0)).isEqualTo("0 m")
    }
}
