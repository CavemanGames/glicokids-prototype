package com.glicokids.prototype.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Module 6 — [AlertDirection.from] turns a raw glucose value plus the configured range into
 * the side that matters for an automatic alert. No number is hardcoded here: the range always
 * comes from the caller.
 */
class AlertDirectionTest {

    @Test
    fun `from returns HYPO below the minimum`() {
        val direction = AlertDirection.from(value = 65, rangeMin = 70, rangeMax = 180)

        assertThat(direction).isEqualTo(AlertDirection.HYPO)
    }

    @Test
    fun `from returns HYPER above the maximum`() {
        val direction = AlertDirection.from(value = 190, rangeMin = 70, rangeMax = 180)

        assertThat(direction).isEqualTo(AlertDirection.HYPER)
    }

    @Test
    fun `from returns NONE in the middle of the range`() {
        val direction = AlertDirection.from(value = 120, rangeMin = 70, rangeMax = 180)

        assertThat(direction).isEqualTo(AlertDirection.NONE)
    }

    @Test
    fun `from returns NONE exactly at the minimum, since the boundary is inside the range`() {
        val direction = AlertDirection.from(value = 70, rangeMin = 70, rangeMax = 180)

        assertThat(direction).isEqualTo(AlertDirection.NONE)
    }

    @Test
    fun `from returns NONE exactly at the maximum, since the boundary is inside the range`() {
        val direction = AlertDirection.from(value = 180, rangeMin = 70, rangeMax = 180)

        assertThat(direction).isEqualTo(AlertDirection.NONE)
    }

    @Test
    fun `from has no hardcoded range, honoring a non-default configuration`() {
        assertThat(AlertDirection.from(value = 75, rangeMin = 80, rangeMax = 200))
            .isEqualTo(AlertDirection.HYPO)
        assertThat(AlertDirection.from(value = 150, rangeMin = 80, rangeMax = 200))
            .isEqualTo(AlertDirection.NONE)
        assertThat(AlertDirection.from(value = 210, rangeMin = 80, rangeMax = 200))
            .isEqualTo(AlertDirection.HYPER)
    }
}
