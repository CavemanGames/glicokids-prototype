package com.glicokids.prototype.presentation.kids

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.local.ReportStorage
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.domain.model.AlertDirection
import com.glicokids.prototype.domain.model.AlertLocationSnapshot
import com.glicokids.prototype.domain.model.AlertMode
import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.LocationTech
import com.glicokids.prototype.domain.model.ReadingSource
import com.glicokids.prototype.domain.repository.SmsGateway
import com.glicokids.prototype.domain.usecase.BuildAlertMessageUseCase
import com.glicokids.prototype.domain.usecase.ResolveAlertLocationUseCase
import com.glicokids.prototype.domain.usecase.ShouldAutoAlertUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    // Module 7: relaxed because most existing tests never touch location at all — only the
    // new tests below stub it explicitly.
    private val resolveAlertLocationUseCase = mockk<ResolveAlertLocationUseCase>(relaxed = true)

    private lateinit var viewModel: GlucoseAlertViewModel

    private val now = 1_700_000_000_000L
    private val message = "GlicoKids: Lucas M. esta com 54 mg/dL as 09:41 - abaixo da faixa (70-180)."

    /**
     * Blocks the test thread (bounded, no busy-wait) until [this] receives a value.
     * Same pattern as `SupportNetworkViewModelTest.getOrAwaitValue` — needed because the
     * send path hops through the real Dispatchers.IO, which an Unconfined test dispatcher
     * cannot observe synchronously.
     *
     * Only safe for the *first* emission a test waits on that also satisfies [until].
     * `observeForever` replays whatever value is already stored the instant it is called, so
     * a second call on a LiveData that already holds a value returns that stale value
     * immediately instead of waiting for a later one — use [awaitNextValue] whenever an
     * action is expected to produce a value distinct from the one already there.
     *
     * [until] defaults to accepting whatever arrives first. Module 7 — once
     * [GlucoseAlertViewModel.start] starts publishing an `isLocating = true` placeholder
     * ahead of the settled state (both on the automatic and the suggest/off paths), a caller
     * that wants the *settled* state on the automatic path — where the eventual send hops
     * through the real `Dispatchers.IO` and resumes asynchronously — must pass
     * `until = { !it.isLocating }` or it will grab that placeholder instead.
     */
    private fun <T> LiveData<T>.getOrAwaitValue(timeoutSeconds: Long = 2, until: (T) -> Boolean = { true }): T {
        var data: T? = null
        val latch = CountDownLatch(1)
        val observer = object : Observer<T> {
            override fun onChanged(value: T) {
                if (!until(value)) return
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
        // Module 7: default consent ON, matching AppPreferences.DEFAULT_ALERT_INCLUDE_LOCATION —
        // a relaxed mock would otherwise return false here, the opposite of the real default.
        every { prefs.alertIncludeLocation } returns true
        every { dbHelper.getAlertRecipients() } returns emptyList()
        // 8 any() since Module 7 added the defaulted locationHint parameter.
        every { buildAlertMessageUseCase.execute(any(), any(), any(), any(), any(), any(), any(), any()) } returns message
        viewModel = GlucoseAlertViewModel(
            dbHelper, prefs, reportStorage, smsGateway, shouldAutoAlertUseCase, buildAlertMessageUseCase,
            resolveAlertLocationUseCase
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
        // sentTo is filtered by the real send result, and the default relaxed mock returns
        // false for an unstubbed suspend call.
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue(until = { !it.isLocating })

        coVerify { smsGateway.sendTextMessage("11988776543", message) }
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
        // lastAlertAt is only written once a send actually confirms, and the default relaxed
        // mock returns false for an unstubbed suspend call — without this stub the throttle is
        // never written and the verify below fails.
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        // Waits for the settled state, not the "locating" placeholder start() publishes first:
        // the throttle is only written once send() comes back off the IO dispatcher, so reading
        // the first emission races that write and fails intermittently.
        viewModel.uiState.getOrAwaitValue(until = { !it.isLocating })

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
        coEvery { smsGateway.sendTextMessage("11900000001", any()) } returns false
        coEvery { smsGateway.sendTextMessage("11900000002", any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue(until = { !it.isLocating })

        coVerify { smsGateway.sendTextMessage("11900000001", message) }
        coVerify { smsGateway.sendTextMessage("11900000002", message) }
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
        coVerify(exactly = 0) { smsGateway.sendTextMessage(any(), any()) }
    }

    @Test
    fun `confirming the suggestion sends to every recipient and records last_alert_at`() {
        val recipients = listOf(sampleContact(id = 1, name = "Ana", phone = "11988776543"))
        every { dbHelper.getAlertRecipients() } returns recipients
        every { prefs.alertModeHyper } returns AlertMode.SUGGEST
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false
        // sentTo and lastAlertAt are gated on the real send result, and the default relaxed
        // mock returns false for an unstubbed suspend call.
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true
        viewModel.start(value = 260, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        // Settling here matters twice over: awaitNextValue below would otherwise consume the
        // suggestion state as if it were the result of confirmSend().
        viewModel.uiState.getOrAwaitValue(until = { !it.isLocating })

        val sent = viewModel.uiState.awaitNextValue { viewModel.confirmSend(nowMillis = now) }

        coVerify { smsGateway.sendTextMessage("11988776543", message) }
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
        coVerify(exactly = 0) { smsGateway.sendTextMessage(any(), any()) }
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

    // --- Async send result must gate what counts as "sent" ---
    //
    // A physical-device test showed "SMS ENVIADO AUTOMATICAMENTE" and a written throttle for
    // a message the carrier silently dropped, because AndroidSmsGateway.sendTextMessage
    // returned `true` just because no exception was thrown. These two tests pin the two
    // symptoms visible at this ViewModel: the throttle must not survive a send that failed,
    // and a recipient whose send failed must not show up in the "already sent" list.
    //
    // Fixing that also flipped three other tests that relied on the default `relaxed`
    // smsGateway mock, which returns `false` for an unstubbed call — the ones covering an
    // automatic send to every recipient, the throttle write, and confirming a suggestion.
    // Each now stubs `coEvery { smsGateway.sendTextMessage(any(), any()) } returns true` for a
    // reason unrelated to this bug: sentTo and lastAlertAt are filtered by the actual send
    // result. Since sendTextMessage suspends, every/verify on it became coEvery/coVerify
    // throughout this file.

    @Test
    fun `does not record last_alert_at when every send fails`() {
        every { dbHelper.getAlertRecipients() } returns listOf(sampleContact())
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns false

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue()

        // Today prefs.lastAlertAt is written before the gateway is even called, unconditionally
        // on the result, so this verify(exactly = 0) fails against the current implementation.
        verify(exactly = 0) { prefs.lastAlertAt = now }
    }

    @Test
    fun `omits a recipient from sentTo when their send fails`() {
        val recipients = listOf(
            sampleContact(id = 1, name = "Ana", phone = "11900000001"),
            sampleContact(id = 2, name = "Beto", phone = "11900000002")
        )
        every { dbHelper.getAlertRecipients() } returns recipients
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { smsGateway.sendTextMessage("11900000001", any()) } returns false
        coEvery { smsGateway.sendTextMessage("11900000002", any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue(until = { !it.isLocating })

        // Today sentTo is built unconditionally from the recipient list regardless of the
        // gateway result, so Ana still shows up as "sent" -> this assertion fails against
        // current code (sentTo has size 2, both names present).
        assertThat(state.sentTo.map { it.name }).containsExactly("Beto")
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
        coVerify(exactly = 0) { smsGateway.sendTextMessage(any(), any()) }
    }

    // --- Achado crítico: btnImOk text must follow the alert direction ---
    //
    // Field observation on a real device: with a 220 mg/dL reading and the "Glicemia alta"
    // title on screen, btnImOk still read "Estou bem — já tomei açúcar" — sugar is the
    // HYPOglycemia treatment, so offering it during a hyperglycemia alert is an inverted
    // clinical instruction shown to a child on an emergency screen. GlucoseAlertViewModel
    // already derives AlertDirection in start() (used above to pick hypoMode/hyperMode); it
    // just never turns that into button copy. These three tests pin the ViewModel side of the
    // fix — the exact text GlucoseAlertUiState must expose per direction — and, most
    // importantly, make sure this specific clinical inversion can never come back unnoticed.
    // NOTE: GlucoseAlertUiState has no `imOkButtonText` field yet — GREEN must add it.

    @Test
    fun `hypo direction keeps the sugar confirmation text on the im-ok button`() {
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false

        // value 54 is below the default rangeMin (70) -> HYPO
        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.imOkButtonText).isEqualTo("Estou bem — já tomei açúcar")
    }

    @Test
    fun `hyper direction confirms the correction was applied on the im-ok button`() {
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false

        // value 260 is above the default rangeMax (180) -> HYPER
        viewModel.start(value = 260, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.imOkButtonText).isEqualTo("Estou bem — já apliquei a correção")
    }

    @Test
    fun `hyper direction never offers sugar on the im-ok button`() {
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false

        // Same setup as above: value 260 -> HYPER. This is the regression guard — the exact
        // defect observed in the field must never be reintroduced by a future simplification.
        viewModel.start(value = 260, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.imOkButtonText).doesNotContain("açúcar")
    }

    // --- Field defect (RED, session of 09/08/2026): three b22 elements with fixed content that
    // never reacts to what the send actually did. All three verified on a physical device.

    // 2.1 — btnResend.visibility is never touched in code (activity_glucose_alert.xml:91): the
    // "Reenviar" button shows up even when nothing has gone out yet, offering to repeat a send
    // that never happened. The only place the design mock shows "Reenviar" (GlicoKids Design.dc.html,
    // id="b22", lines ~765-771 and its f22 pair ~1708-1714) is right inside the llSentTo block —
    // never alone, never next to llConfirmActions — so the mock's own composition already ties
    // "Reenviar" to "something was already sent", not to unconditional visibility.

    @Test
    fun `showResendAction is false before anything has actually been sent`() {
        every { prefs.alertModeHypo } returns AlertMode.SUGGEST
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.sentTo).isEmpty()
        assertThat(state.showResendAction).isFalse()
    }

    @Test
    fun `showResendAction turns true only once a send actually delivered to someone`() {
        every { dbHelper.getAlertRecipients() } returns listOf(sampleContact())
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue(until = { !it.isLocating })

        assertThat(state.sentTo).isNotEmpty()
        assertThat(state.showResendAction).isTrue()
    }

    // 2.2 — tvThrottleNotice always shows "próximo alerta só daqui a X min" (activity_glucose_alert.xml:106-110
    // + GlucoseAlertActivity.observeViewModel), even when last_alert_at was never written.
    // Verified on device: a MANUAL reading of 55 showed the notice without recording any throttle.

    @Test
    fun `the field regression - a manual reading with no alert ever sent does not claim a throttle window`() {
        every { prefs.lastAlertAt } returns 0L
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false // ShouldAutoAlertUseCaseTest already proves MANUAL never auto-sends

        viewModel.start(value = 55, timestampMillis = now, source = ReadingSource.MANUAL, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.showThrottleNotice).isFalse()
    }

    @Test
    fun `showThrottleNotice turns true once this alert actually sends`() {
        every { dbHelper.getAlertRecipients() } returns listOf(sampleContact())
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue(until = { !it.isLocating })

        assertThat(state.showThrottleNotice).isTrue()
    }

    @Test
    fun `showThrottleNotice stays true while an earlier alert's window has not elapsed, even without a new send`() {
        every { prefs.lastAlertAt } returns now - 5 * 60_000L
        every { prefs.alertThrottleMin } returns 30
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false // still inside the 30-minute window from an earlier alert

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.showThrottleNotice).isTrue()
    }

    // 2.3 — with SEND_SMS revoked, AndroidSmsGateway.sendTextMessage catches the SecurityException
    // and returns `false`, the same catch-all already used for any delivery failure — so from the
    // ViewModel's side "permission denied" and "every send attempt failed" are the SAME signal:
    // it never needs a new channel from Context (which it cannot touch), only to react to the
    // result SmsGateway already hands back. handoff-android.md line 241: "Permissão negada nunca
    // derruba a tela: toast explicando e o botão continua clicável." Today showConfirmActions is
    // set to false after ANY send() (successful or not), so tapping "Enviar agora" makes every
    // button disappear with no explanation at all — verified on device.

    @Test
    fun `the field regression - confirming a send that reaches nobody surfaces an explanation instead of just hiding the buttons`() {
        val recipients = listOf(sampleContact(id = 1, name = "Ana", phone = "11988776543"))
        every { dbHelper.getAlertRecipients() } returns recipients
        every { prefs.alertModeHyper } returns AlertMode.SUGGEST
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns false // e.g. SEND_SMS revoked

        viewModel.start(value = 260, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue()
        val failed = viewModel.uiState.awaitNextValue { viewModel.confirmSend(nowMillis = now) }

        assertThat(failed.sentTo).isEmpty()
        assertThat(failed.sendFailedMessage).isNotNull()
    }

    @Test
    fun `sendFailedMessage stays null when the send actually reaches someone`() {
        every { dbHelper.getAlertRecipients() } returns listOf(sampleContact())
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.sendFailedMessage).isNull()
    }

    @Test
    fun `sendFailedMessage stays null when nothing was attempted at all`() {
        every { prefs.alertModeHypo } returns AlertMode.OFF
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns false

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.sendFailedMessage).isNull()
    }

    // --- Module 7 (RED): the alert screen gains a location hint ---
    //
    // start()/send() are untouched in production so far — ResolveAlertLocationUseCase is
    // injected but never called. Every test below that expects locationLabel/isLocating to be
    // populated, or that resolveAlertLocationUseCase was actually consulted, fails today for
    // that reason; GREEN wiring it in is what turns them green. The two tests marked "guard"
    // are the opposite: they already pass, and must keep passing forever — they pin the
    // invariant that a location failure (or, here, no wiring at all yet) can never block,
    // delay or change the alert send.

    private val locationPoint = GeoPoint(
        lat = -23.55, lng = -46.63, accuracyMeters = 15f, tech = LocationTech.GPS, timestampMillis = 1_700_000_000_000L
    )

    @Test
    fun `guard - does not consult location resolution at all when the location preference is off`() {
        every { prefs.alertIncludeLocation } returns false
        every { dbHelper.getAlertRecipients() } returns listOf(sampleContact())
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue()

        coVerify(exactly = 0) { resolveAlertLocationUseCase.execute(any()) }
    }

    // Product decision superseded the original shape of this guard (session of 16/08/2026):
    // start() now awaits resolveAlertLocationUseCase.execute(...) before building/sending the
    // message, on purpose — an auto-send may take up to BUDGET_MILLIS longer so the SMS can
    // carry an address. That is safe only because ResolveAlertLocationUseCase promises to
    // always return within its own budget and never throw — proven independently, with real
    // virtual-time control, by ResolveAlertLocationUseCaseTest (see especially "a fix that only
    // arrives after this use case's own budget counts as no fix"). A mock that hangs forever via
    // delay(Long.MAX_VALUE) bypasses that real contract and cannot resolve on this test's plain
    // UnconfinedTestDispatcher (there is no virtual-time driver here to elapse it), so it no
    // longer models a scenario the real use case can ever produce. What this guard still owes
    // the suite is proof that a *settled* empty resolution — the actual terminal outcome the
    // real use case always reaches — never blocks the alert from reaching its recipients.
    @Test
    fun `guard - the alert still reaches every recipient even when location resolution finds nothing`() {
        every { prefs.alertIncludeLocation } returns true
        coEvery { resolveAlertLocationUseCase.execute(any()) } returns null
        every { dbHelper.getAlertRecipients() } returns listOf(sampleContact())
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue(until = { !it.isLocating })

        assertThat(state.sentTo).isNotEmpty()
        coVerify { smsGateway.sendTextMessage("11988776543", any()) }
    }

    @Test
    fun `once consent is on and location resolves, the sent message carries the location hint`() {
        every { prefs.alertIncludeLocation } returns true
        coEvery { resolveAlertLocationUseCase.execute(true) } returns
            AlertLocationSnapshot(point = locationPoint, label = "Rua Tal, 123")
        every { dbHelper.getAlertRecipients() } returns listOf(sampleContact())
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true
        every {
            buildAlertMessageUseCase.execute(
                partialChildName = any(), value = any(), timestampMillis = any(),
                rangeMin = any(), rangeMax = any(), isHypo = any(), fromSensor = any(),
                locationHint = "Rua Tal, 123"
            )
        } returns "$message (Rua Tal, 123)"

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        val state = viewModel.uiState.getOrAwaitValue(until = { !it.isLocating })

        assertThat(state.messagePreview).contains("Rua Tal, 123")
        assertThat(state.locationLabel).isEqualTo("Rua Tal, 123")
    }

    @Test
    fun `once consent is on and location resolves, the last alert location is written to preferences`() {
        every { prefs.alertIncludeLocation } returns true
        coEvery { resolveAlertLocationUseCase.execute(true) } returns
            AlertLocationSnapshot(point = locationPoint, label = "Rua Tal, 123")
        every { dbHelper.getAlertRecipients() } returns listOf(sampleContact())
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue(until = { !it.isLocating })

        verify {
            prefs.saveLastAlertLocation(lat = -23.55, lng = -46.63, label = "Rua Tal, 123", atMillis = now)
        }
    }

    @Test
    fun `location resolution failing does not write any last alert location`() {
        every { prefs.alertIncludeLocation } returns true
        coEvery { resolveAlertLocationUseCase.execute(true) } returns null
        every { dbHelper.getAlertRecipients() } returns listOf(sampleContact())
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true
        coEvery { smsGateway.sendTextMessage(any(), any()) } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)
        viewModel.uiState.getOrAwaitValue()

        verify(exactly = 0) { prefs.saveLastAlertLocation(any(), any(), any(), any()) }
    }

    // Design assumption flagged for confirmation (see report): this pins isLocating to an
    // emission published before the location resolution suspends, observable synchronously
    // under UnconfinedTestDispatcher. If GREEN's chosen shape emits the searching state some
    // other way, this specific test may need adjusting — the requirement it specifies (b22 has
    // something to show while the async resolution is in flight) does not.
    @Test
    fun `start shows isLocating true immediately while the location resolution is still pending`() {
        every { prefs.alertIncludeLocation } returns true
        coEvery { resolveAlertLocationUseCase.execute(any()) } coAnswers { delay(Long.MAX_VALUE); null }
        every {
            shouldAutoAlertUseCase.execute(any(), any(), any(), any(), any(), any(), any())
        } returns true

        viewModel.start(value = 54, timestampMillis = now, source = ReadingSource.SENSOR, nowMillis = now)

        val state = viewModel.uiState.value
        assertThat(state).isNotNull()
        assertThat(state!!.isLocating).isTrue()
    }
}
