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

    // --- Module 8 (SPEC-MODULO-8-SOM.md §11, cases 18-20) ---
    // RED phase: `AlertSettingsUiState.soundEnabled`/`medalSoundUri`/`alertSoundUri`/
    // `doseSoundUri` are placeholder defaults (see AlertSettingsViewModel kdoc) and
    // `setSoundEnabled`/`setMedalSound`/`setAlertSound`/`setDoseSound` are `TODO()` skeletons —
    // every test below is expected to fail until the next phase wires the real logic.

    // Case 18
    @Test
    fun `initial state reflects sound prefs and the three stored uris`() {
        every { prefs.soundEnabled } returns true
        every { prefs.medalSoundUri } returns "content://medal"
        every { prefs.alertSoundUri } returns "content://alert"
        every { prefs.doseSoundUri } returns "content://dose"
        val vm = AlertSettingsViewModel(prefs, dbHelper)

        val state = vm.uiState.value!!

        assertThat(state.soundEnabled).isTrue()
        assertThat(state.medalSoundUri).isEqualTo("content://medal")
        assertThat(state.alertSoundUri).isEqualTo("content://alert")
        assertThat(state.doseSoundUri).isEqualTo("content://dose")
    }

    // Case 19
    @Test
    fun `setMedalSound writes only the medal sound uri`() {
        viewModel.setMedalSound("content://medal-x")

        verify { prefs.medalSoundUri = "content://medal-x" }
        verify(exactly = 0) { prefs.alertSoundUri = any() }
        verify(exactly = 0) { prefs.doseSoundUri = any() }
    }

    @Test
    fun `setAlertSound writes only the alert sound uri`() {
        viewModel.setAlertSound("content://alert-x")

        verify { prefs.alertSoundUri = "content://alert-x" }
        verify(exactly = 0) { prefs.medalSoundUri = any() }
        verify(exactly = 0) { prefs.doseSoundUri = any() }
    }

    @Test
    fun `setDoseSound writes only the dose sound uri`() {
        viewModel.setDoseSound("content://dose-x")

        verify { prefs.doseSoundUri = "content://dose-x" }
        verify(exactly = 0) { prefs.medalSoundUri = any() }
        verify(exactly = 0) { prefs.alertSoundUri = any() }
    }

    @Test
    fun `setMedalSound accepts null to store the silent sentinel translation`() {
        viewModel.setMedalSound(null)

        verify { prefs.medalSoundUri = null }
    }

    // Case 20
    @Test
    fun `setSoundEnabled writes the general switch without touching stored uris`() {
        viewModel.setSoundEnabled(false)

        verify { prefs.soundEnabled = false }
        verify(exactly = 0) { prefs.medalSoundUri = any() }
        verify(exactly = 0) { prefs.alertSoundUri = any() }
        verify(exactly = 0) { prefs.doseSoundUri = any() }
    }

    @Test
    fun `setSoundEnabled toggles back on`() {
        viewModel.setSoundEnabled(true)

        verify { prefs.soundEnabled = true }
    }
}
