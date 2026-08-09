package com.glicokids.prototype.presentation.kids

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.local.ReportStorage
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.domain.model.AlertDirection
import com.glicokids.prototype.domain.model.AlertMode
import com.glicokids.prototype.domain.model.ReadingSource
import com.glicokids.prototype.domain.repository.SmsGateway
import com.glicokids.prototype.domain.usecase.BuildAlertMessageUseCase
import com.glicokids.prototype.domain.usecase.ShouldAutoAlertUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Module 6 — b22, the glucose alert screen opened from the reading flow. Consults
 * [ShouldAutoAlertUseCase] to decide between an automatic send, a suggestion the
 * caregiver has to confirm, or nothing at all, and never assembles its own message
 * text — every SMS body it sends comes straight out of [BuildAlertMessageUseCase].
 */
class GlucoseAlertViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dbHelper = mockk<GlicoKidsDbHelper>(relaxed = true)
    private val prefs = mockk<AppPreferences>(relaxed = true)
    private val reportStorage = mockk<ReportStorage>(relaxed = true)
    private val smsGateway = mockk<SmsGateway>(relaxed = true)
    private val shouldAutoAlertUseCase = mockk<ShouldAutoAlertUseCase>()
    private val buildAlertMessageUseCase = mockk<BuildAlertMessageUseCase>()

    private lateinit var viewModel: GlucoseAlertViewModel

    private val now = 1_700_000_000_000L
    private val message = "GlicoKids: Lucas M. esta com 54 mg/dL as 09:41 - abaixo da faixa (70-180)."

    /**
     * Blocks the test thread (bounded, no busy-wait) until [this] receives a value.
     * Same pattern as `SupportNetworkViewModelTest.getOrAwaitValue` — needed because the
     * send path hops through the real Dispatchers.IO, which an Unconfined test dispatcher
     * cannot observe synchronously.
     *
     * Only safe for the *first* emission a test waits on. `observeForever` replays
     * whatever value is already stored the instant it is called, so a second call on a
     * LiveData that already holds a value returns that stale value immediately instead of
     * waiting for a later one — use [awaitNextValue] whenever an action is expected to
     * produce a value distinct from the one already there.
     */
    private fun <T> LiveData<T>.getOrAwaitValue(timeoutSeconds: Long = 2): T {
        var data: T? = null
        val latch = CountDownLatch(1)
        val observer = object : Observer<T> {
            override fun onChanged(value: T) {
                data = value
                latch.countDown()
                this@getOrAwaitValue.removeObserver(this)
            }
        }
        observeForever(observer)
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            throw TimeoutException("LiveData value never set within ${timeoutSeconds}s.")
        }
        @Suppress("UNCHECKED_CAST")
        return data as T
    }

    /**
     * Runs [action] and blocks (bounded, no busy-wait) until [this] emits a value distinct
     * from the one already stored, then returns it. The observer is registered before
     * [action] runs, so an emission racing the registration is never missed the way it
     * would be with a plain `getOrAwaitValue()` call after the fact.
     *
     * The ViewModel never mutates a state object in place — every `_uiState.value = ...`
     * assigns a fresh data class instance — so reference identity against the value
     * captured before [action] runs reliably tells a stale replay apart from a genuinely
     * new emission, without needing a flag to track subscription timing.
     */
    private fun <T> LiveData<T>.awaitNextValue(timeoutSeconds: Long = 2, action: () -> Unit): T {
        val previousValue = value
        var data: T? = null
        val latch = CountDownLatch(1)
        val observer = object : Observer<T> {
            override fun onChanged(value: T) {
                if (value === previousValue) return
                data = value
                latch.countDown()
                this@awaitNextValue.removeObserver(this)
            }
        }
        observeForever(observer)
        action()
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            removeObserver(observer)
            throw TimeoutException("LiveData did not emit a new value within ${timeoutSeconds}s.")
        }
        @Suppress("UNCHECKED_CAST")
        return data as T
    }

    private fun sampleContact(
        id: Long = 1,
        name: String = "Ana",
        relationship: String = "Mae",
        phone: String = "11988776543"
    ) = Contact(
        id = id,
        name = name,
        relationship = relationship,
        phone = phone,
        email = null,
        receivesAlert = true,
        receivesReport = false,
        isPrimary = false,
        createdAt = 0L
    )

    @Before
    fun setup() {
        // The send path hops through withContext(Dispatchers.IO) — a real dispatcher —
        // so Dispatchers.Main needs a live implementation. Unconfined runs the coroutine
        // eagerly, including the resumption posted back from the real IO thread.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { prefs.rangeMin } returns 70
        every { prefs.rangeMax } returns 180
        every { prefs.alertModeHypo } returns AlertMode.AUTO
        every { prefs.alertModeHyper } returns AlertMode.SUGGEST
        every { prefs.alertThrottleMin } returns 30
        every { prefs.lastAlertAt } returns 0L
        every { dbHelper.getAlertRecipients() } returns emptyList()
        every { buildAlertMessageUseCase.execute(any(), any(), any(), any(), any(), any(), any()) } returns message
        viewModel = GlucoseAlertViewModel(
            dbHelper, prefs, reportStorage, smsGateway, shouldAutoAlertUseCase, buildAlertMessageUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- ShouldAutoAlertUseCase returns true: automatic send ---

    @Test
    fun `use case true sends to every alert recipient, hides confirm actions and shows the sent list`() {
        val recipients = listOf(sampleContact(id = 1, name = "Ana"), sampleContact(id = 2, name = "Beto"))
        every { dbHelper.getAlertRecipients() } returns recipients
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue()

        verify { smsGateway.sendTextMessage("11988776543", message) }
        assertThat(state.sentTo).hasSize(2)
        assertThat(state.showConfirmActions).isFalse()
        assertThat(state.messagePreview).isEqualTo(message)
    }

    @Test
    fun `use case true records last_alert_at`() {
        every { dbHelper.getAlertRecipients() } returns listOf(sampleContact())
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue()

        verify { prefs.lastAlertAt = now }
    }

    @Test
    fun `a send failure for one recipient does not block sending to the others`() {
        val recipients = listOf(
            sampleContact(id = 1, name = "Ana", phone = "11900000001"),
            sampleContact(id = 2, name = "Beto", phone = "11900000002")
        )
        every { dbHelper.getAlertRecipients() } returns recipients
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        every { smsGateway.sendTextMessage("11900000001", any()) } returns false
        every { smsGateway.sendTextMessage("11900000002", any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue()

        verify { smsGateway.sendTextMessage("11900000001", message) }
        verify { smsGateway.sendTextMessage("11900000002", message) }
    }

    // --- ShouldAutoAlertUseCase returns false: SUGGEST vs OFF ---

    @Test
    fun `use case false with suggest mode shows confirm actions and sends nothing until confirmed`() {
        val recipients = listOf(sampleContact(id = 1, name = "Ana", phone = "11988776543"))
        every { dbHelper.getAlertRecipients() } returns recipients
        every { prefs.alertModeHyper } returns AlertMode.SUGGEST
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false

        // value above the range -> hyper direction, whose mode is SUGGEST
        viewModel.start(value = 260, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val suggested = viewModel.uiState.getOrAwaitValue()

        assertThat(suggested.showConfirmActions).isTrue()
        assertThat(suggested.sentTo).isEmpty()
        assertThat(suggested.messagePreview).isEqualTo(message)
        verify(exactly = 0) { smsGateway.sendTextMessage(any(), any()) }
    }

    @Test
    fun `confirming the suggestion sends to every recipient and records last_alert_at`() {
        val recipients = listOf(sampleContact(id = 1, name = "Ana", phone = "11988776543"))
        every { dbHelper.getAlertRecipients() } returns recipients
        every { prefs.alertModeHyper } returns AlertMode.SUGGEST
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false
        viewModel.start(value = 260, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue()

        val sent = viewModel.uiState.awaitNextValue { viewModel.confirmSend(nowMillis = now) }

        verify { smsGateway.sendTextMessage("11988776543", message) }
        verify { prefs.lastAlertAt = now }
        assertThat(sent.showConfirmActions).isFalse()
        assertThat(sent.sentTo).hasSize(1)
    }

    @Test
    fun `use case false with off mode neither sends nor suggests`() {
        every { prefs.alertModeHypo } returns AlertMode.OFF
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false

        // value below the range -> hypo direction, whose mode is OFF
        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.showConfirmActions).isFalse()
        assertThat(state.sentTo).isEmpty()
        verify(exactly = 0) { smsGateway.sendTextMessage(any(), any()) }
    }

    // --- Correct repass of source/direction into the use case ---
    //
    // ShouldAutoAlertUseCase is the single, named place that decides automatic sending
    // (ShouldAutoAlertUseCaseTest already proves MANUAL never fires there) — the ViewModel
    // must never re-check `source == MANUAL` itself, or the safety rule ends up written
    // twice and one copy can silently drift from the other. What the ViewModel CAN get
    // wrong on its own is failing to forward the real reading source, or computing the
    // wrong direction from the current range prefs. These two tests capture the exact
    // arguments handed to the use case to pin that down instead.

    @Test
    fun `forwards the exact reading source and a range-derived direction for a manual reading`() {
        val sourceSlot = slot<ReadingSource>()
        val directionSlot = slot<AlertDirection>()
        every {
            shouldAutoAlertUseCase.execute(
                source = capture(sourceSlot),
                direction = capture(directionSlot),
                hypoMode = any(),
                hyperMode = any(),
                lastAlertAt = any(),
                now = any(),
                throttleMin = any()
            )
        } returns false

        // value 54 is below the default rangeMin (70) -> HYPO
        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.MANUAL, nowMillis = now)
        viewModel.uiState.getOrAwaitValue()

        assertThat(sourceSlot.captured).isEqualTo(ReadingSource.MANUAL)
        assertThat(directionSlot.captured).isEqualTo(AlertDirection.HYPO)
    }

    @Test
    fun `forwards the exact reading source and a range-derived direction for a sensor reading`() {
        val sourceSlot = slot<ReadingSource>()
        val directionSlot = slot<AlertDirection>()
        every {
            shouldAutoAlertUseCase.execute(
                source = capture(sourceSlot),
                direction = capture(directionSlot),
                hypoMode = any(),
                hyperMode = any(),
                lastAlertAt = any(),
                now = any(),
                throttleMin = any()
            )
        } returns false

        // value 260 is above the default rangeMax (180) -> HYPER
        viewModel.start(value = 260, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue()

        assertThat(sourceSlot.captured).isEqualTo(ReadingSource.SENSOR)
        assertThat(directionSlot.captured).isEqualTo(AlertDirection.HYPER)
    }

    // --- Throttle ---

    @Test
    fun `forwards last_alert_at and the configured throttle into the use case and sends nothing while it blocks`() {
        every { prefs.lastAlertAt } returns now - 5 * 60_000L
        every { prefs.alertThrottleMin } returns 30
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue()

        verify {
            shouldAutoAlertUseCase.execute(
                source = ReadingSource.SENSOR,
                direction = any(),
                hypoMode = any(),
                hyperMode = any(),
                lastAlertAt = now - 5 * 60_000L,
                now = now,
                throttleMin = 30
            )
        }
        verify(exactly = 0) { smsGateway.sendTextMessage(any(), any()) }
    }
}
