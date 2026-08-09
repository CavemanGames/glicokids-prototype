package com.glicokids.prototype.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Module 6 — the pt-BR text sent to the support network. The use case receives only
 * primitives: the partial name is built elsewhere (`ReportStorage.anonymizedName`,
 * LGPD rule already covered by ReportStorageTest) and simply passed through here.
 */
class BuildAlertMessageUseCaseTest {

    private val useCase = BuildAlertMessageUseCase()

    @Test
    fun `hypo sensor reading mentions partial name, value, time, range and sensor origin`() {
        val timestamp = fixedTimestamp(hour = 9, minute = 41)

        val message = useCase.execute(
            partialChildName = "Lucas M.",
            value = 54,
            timestampMillis = timestamp,
            rangeMin = 70,
            rangeMax = 180,
            isHypo = true,
            fromSensor = true
        )

        assertThat(message).contains("Lucas M.")
        assertThat(message).contains("54 mg/dL")
        assertThat(message).contains(expectedTime(timestamp))
        assertThat(message).contains("70–180")
        assertThat(message).contains("abaixo da faixa")
        assertThat(message).contains("sensor")
    }

    @Test
    fun `hyper reading mentions above the range instead of below`() {
        val message = useCase.execute(
            partialChildName = "Lucas M.",
            value = 260,
            timestampMillis = fixedTimestamp(hour = 14, minute = 5),
            rangeMin = 70,
            rangeMax = 180,
            isHypo = false,
            fromSensor = true
        )

        assertThat(message).contains("acima da faixa")
        assertThat(message).doesNotContain("abaixo da faixa")
    }

    @Test
    fun `never adds anything beyond the partial name it received`() {
        val message = useCase.execute(
            partialChildName = "Lucas M.",
            value = 54,
            timestampMillis = fixedTimestamp(hour = 9, minute = 41),
            rangeMin = 70,
            rangeMax = 180,
            isHypo = true,
            fromSensor = true
        )

        assertThat(message).contains("Lucas M.")
        assertThat(message).doesNotContain("Lucas Silva")
        assertThat(message).doesNotContain("Souza")
    }

    @Test
    fun `sensor origin is mentioned only when the reading came from the sensor`() {
        val fromSensor = useCase.execute(
            partialChildName = "Lucas M.",
            value = 54,
            timestampMillis = fixedTimestamp(hour = 9, minute = 41),
            rangeMin = 70,
            rangeMax = 180,
            isHypo = true,
            fromSensor = true
        )
        val manual = useCase.execute(
            partialChildName = "Lucas M.",
            value = 54,
            timestampMillis = fixedTimestamp(hour = 9, minute = 41),
            rangeMin = 70,
            rangeMax = 180,
            isHypo = true,
            fromSensor = false
        )

        assertThat(fromSensor).contains("sensor")
        assertThat(manual).doesNotContain("sensor")
    }

    private fun fixedTimestamp(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.AUGUST, 8, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun expectedTime(timestampMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(timestampMillis))
}
