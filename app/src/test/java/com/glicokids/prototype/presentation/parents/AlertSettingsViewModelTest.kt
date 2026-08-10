package com.glicokids.prototype.presentation.parents

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.domain.model.AlertMode
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AlertSettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val prefs = mockk<AppPreferences>(relaxed = true)
    private val dbHelper = mockk<GlicoKidsDbHelper>(relaxed = true)
    private lateinit var viewModel: AlertSettingsViewModel

    private fun sampleRecipient(id: Long) = Contact(
        id = id,
        name = "Contato $id",
        relationship = "Mae",
        phone = "11999990000",
        email = "contato$id@example.com",
        receivesAlert = true,
        receivesReport = false,
        isPrimary = false,
        createdAt = 0L
    )

    @Before
    fun setup() {
        every { prefs.alertModeHypo } returns AlertMode.AUTO
        every { prefs.alertModeHyper } returns AlertMode.SUGGEST
        every { prefs.alertThrottleMin } returns 30
        every { prefs.alertOnRecovery } returns true
        every { prefs.rangeMin } returns 70
        every { prefs.rangeMax } returns 180
        every { dbHelper.getAlertRecipients() } returns listOf(sampleRecipient(1))
        viewModel = AlertSettingsViewModel(prefs, dbHelper)
    }

    @Test
    fun `estado inicial reflete os modos de fabrica das prefs`() {
        val state = viewModel.uiState.value!!

        assertThat(state.hypoMode).isEqualTo(AlertMode.AUTO)
        assertThat(state.hyperMode).isEqualTo(AlertMode.SUGGEST)
    }

    @Test
    fun `mudar o modo de hipo grava so alert_mode_hypo e nao mexe no hiper`() {
        viewModel.setHypoMode(AlertMode.OFF)

        verify { prefs.alertModeHypo = AlertMode.OFF }
        verify(exactly = 0) { prefs.alertModeHyper = any() }
    }

    @Test
    fun `mudar o modo de hiper grava so alert_mode_hyper e nao mexe no hipo`() {
        viewModel.setHyperMode(AlertMode.OFF)

        verify { prefs.alertModeHyper = AlertMode.OFF }
        verify(exactly = 0) { prefs.alertModeHypo = any() }
    }

    @Test
    fun `os tres modos sao gravaveis do lado hipo`() {
        AlertMode.values().forEach { mode ->
            viewModel.setHypoMode(mode)
            verify { prefs.alertModeHypo = mode }
        }
    }

    @Test
    fun `os tres modos sao gravaveis do lado hiper`() {
        AlertMode.values().forEach { mode ->
            viewModel.setHyperMode(mode)
            verify { prefs.alertModeHyper = mode }
        }
    }

    @Test
    fun `throttle grava alert_throttle_min para os tres valores da tela`() {
        listOf(15, 30, 60).forEach { minutes ->
            viewModel.setThrottleMin(minutes)
            verify { prefs.alertThrottleMin = minutes }
        }
    }

    @Test
    fun `aviso de recuperacao alterna e persiste nos dois sentidos`() {
        viewModel.setRecoveryNoticeEnabled(false)
        verify { prefs.alertOnRecovery = false }

        viewModel.setRecoveryNoticeEnabled(true)
        verify { prefs.alertOnRecovery = true }
    }

    @Test
    fun `limites exibidos vem da faixa vigente das prefs, nao de 70 ou 180 fixos`() {
        every { prefs.rangeMin } returns 80
        every { prefs.rangeMax } returns 200
        val vm = AlertSettingsViewModel(prefs, dbHelper)

        val state = vm.uiState.value!!

        assertThat(state.hypoLimit).isEqualTo(80)
        assertThat(state.hyperLimit).isEqualTo(200)
    }

    @Test
    fun `contagem de destinatarios vem de getAlertRecipients`() {
        every { dbHelper.getAlertRecipients() } returns listOf(sampleRecipient(1), sampleRecipient(2))
        val vm = AlertSettingsViewModel(prefs, dbHelper)

        assertThat(vm.uiState.value!!.recipientCount).isEqualTo(2)
    }
}
