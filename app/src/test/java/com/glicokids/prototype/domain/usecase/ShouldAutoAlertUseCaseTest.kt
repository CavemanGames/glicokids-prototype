package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.domain.model.AlertDirection
import com.glicokids.prototype.domain.model.AlertMode
import com.glicokids.prototype.domain.model.ReadingSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Module 6 — the safety-critical rule: a MANUAL reading never fires an alert by itself
 * (the child is awake and typing), and each direction (hypo/hyper) reads its own mode.
 */
class ShouldAutoAlertUseCaseTest {

    private val useCase = ShouldAutoAlertUseCase()

    private val now = 1_000_000_000L
    private val throttleMin = 30
    private val longAgo = now - (throttleMin + 1) * 60_000L

    @Test
    fun `manual source never triggers an alert, for every direction and every mode combination`() {
        val directions = listOf(AlertDirection.HYPO, AlertDirection.HYPER, AlertDirection.NONE)
        val modes = listOf(AlertMode.AUTO, AlertMode.SUGGEST, AlertMode.OFF)

        for (direction in directions) {
            for (hypoMode in modes) {
                for (hyperMode in modes) {
                    val result = useCase.execute(
                        source = ReadingSource.MANUAL,
                        direction = direction,
                        hypoMode = hypoMode,
                        hyperMode = hyperMode,
                        lastAlertAt = longAgo,
                        now = now,
                        throttleMin = throttleMin
                    )

                    assertThat(result).isFalse()
                }
            }
        }
    }

    @Test
    fun `direction none never triggers an alert even with sensor and both modes on auto`() {
        val result = useCase.execute(
            source = ReadingSource.SENSOR,
            direction = AlertDirection.NONE,
            hypoMode = AlertMode.AUTO,
            hyperMode = AlertMode.AUTO,
            lastAlertAt = longAgo,
            now = now,
            throttleMin = throttleMin
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `sensor reading with hypo direction triggers an alert when hypo mode is auto`() {
        val result = useCase.execute(
            source = ReadingSource.SENSOR,
            direction = AlertDirection.HYPO,
            hypoMode = AlertMode.AUTO,
            hyperMode = AlertMode.OFF,
            lastAlertAt = longAgo,
            now = now,
            throttleMin = throttleMin
        )

        assertThat(result).isTrue()
    }

    @Test
    fun `sensor reading with hyper direction does not trigger an alert when hyper mode is off`() {
        val result = useCase.execute(
            source = ReadingSource.SENSOR,
            direction = AlertDirection.HYPER,
            hypoMode = AlertMode.AUTO,
            hyperMode = AlertMode.OFF,
            lastAlertAt = longAgo,
            now = now,
            throttleMin = throttleMin
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `hypo mode off blocks a hypo alert even though hyper mode is auto, proving the modes are independent`() {
        val result = useCase.execute(
            source = ReadingSource.SENSOR,
            direction = AlertDirection.HYPO,
            hypoMode = AlertMode.OFF,
            hyperMode = AlertMode.AUTO,
            lastAlertAt = longAgo,
            now = now,
            throttleMin = throttleMin
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `hyper mode off blocks a hyper alert even though hypo mode is auto, proving the modes are independent`() {
        val result = useCase.execute(
            source = ReadingSource.SENSOR,
            direction = AlertDirection.HYPER,
            hypoMode = AlertMode.AUTO,
            hyperMode = AlertMode.OFF,
            lastAlertAt = longAgo,
            now = now,
            throttleMin = throttleMin
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `suggest mode never auto-triggers a hypo alert, only auto does`() {
        val result = useCase.execute(
            source = ReadingSource.SENSOR,
            direction = AlertDirection.HYPO,
            hypoMode = AlertMode.SUGGEST,
            hyperMode = AlertMode.OFF,
            lastAlertAt = longAgo,
            now = now,
            throttleMin = throttleMin
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `suggest mode never auto-triggers a hyper alert, only auto does`() {
        val result = useCase.execute(
            source = ReadingSource.SENSOR,
            direction = AlertDirection.HYPER,
            hypoMode = AlertMode.OFF,
            hyperMode = AlertMode.SUGGEST,
            lastAlertAt = longAgo,
            now = now,
            throttleMin = throttleMin
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `throttle blocks a repeated alert within the window`() {
        val result = useCase.execute(
            source = ReadingSource.SENSOR,
            direction = AlertDirection.HYPO,
            hypoMode = AlertMode.AUTO,
            hyperMode = AlertMode.OFF,
            lastAlertAt = now - 5 * 60_000L, // 5 min ago, throttle is 30 min
            now = now,
            throttleMin = throttleMin
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `throttle releases the alert once the window has elapsed`() {
        val result = useCase.execute(
            source = ReadingSource.SENSOR,
            direction = AlertDirection.HYPO,
            hypoMode = AlertMode.AUTO,
            hyperMode = AlertMode.OFF,
            lastAlertAt = now - 31 * 60_000L, // just past the 30 min throttle
            now = now,
            throttleMin = throttleMin
        )

        assertThat(result).isTrue()
    }

    @Test
    fun `throttle releases the alert exactly at the window boundary, since the guard is a strict less-than`() {
        val result = useCase.execute(
            source = ReadingSource.SENSOR,
            direction = AlertDirection.HYPO,
            hypoMode = AlertMode.AUTO,
            hyperMode = AlertMode.OFF,
            lastAlertAt = now - throttleMin * 60_000L, // exactly the configured window
            now = now,
            throttleMin = throttleMin
        )

        assertThat(result).isTrue()
    }
}
