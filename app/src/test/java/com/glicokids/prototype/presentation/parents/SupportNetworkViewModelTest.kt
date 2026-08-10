package com.glicokids.prototype.presentation.parents

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.local.ReportStorage
import com.glicokids.prototype.data.model.Contact
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
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

class SupportNetworkViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dbHelper = mockk<GlicoKidsDbHelper>(relaxed = true)
    private val reportStorage = mockk<ReportStorage>(relaxed = true)
    private lateinit var viewModel: SupportNetworkViewModel

    /**
     * Blocks the test thread (bounded, no busy-wait) until [this] receives a value.
     * Needed for `shareSummary`, whose viewModelScope coroutine hops through the
     * real Dispatchers.IO — a StandardTestDispatcher-based advanceUntilIdle() cannot
     * see that hand-off, so the assertion has to wait on the LiveData itself instead
     * of on the coroutine's virtual clock. Common pattern (Google's `android-architecture`
     * `LiveDataTestUtil.getOrAwaitValue`).
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

    private fun sampleContact(
        id: Long = 1,
        name: String = "Ana",
        phone: String = "11987654321",
        email: String? = "ana@example.com",
        receivesAlert: Boolean = true,
        receivesReport: Boolean = false,
        isPrimary: Boolean = false
    ) = Contact(
        id = id,
        name = name,
        relationship = "Mae",
        phone = phone,
        email = email,
        receivesAlert = receivesAlert,
        receivesReport = receivesReport,
        isPrimary = isPrimary,
        createdAt = 0L
    )

    @Before
    fun setup() {
        // shareSummary() hops through withContext(Dispatchers.IO) — a real dispatcher —
        // so Dispatchers.Main needs a live implementation (there is none in a plain JVM
        // test). Unconfined runs the coroutine eagerly, including the resumption posted
        // back from the real IO thread, so no scheduler pump is required from this test.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { dbHelper.getContacts() } returns emptyList()
        viewModel = SupportNetworkViewModel(dbHelper, reportStorage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Contact validation, one rule per test ---

    @Test
    fun `nome em branco e recusado`() {
        val saved = viewModel.saveContact(
            existing = null, name = "", relationship = "Mae", phone = "11987654321",
            email = null, receivesAlert = true, receivesReport = false
        )

        assertThat(saved).isFalse()
        verify(exactly = 0) { dbHelper.insertContact(any()) }
        assertThat(viewModel.validationError.value).isNotNull()
    }

    @Test
    fun `telefone com menos de 10 digitos e recusado`() {
        val saved = viewModel.saveContact(
            existing = null, name = "Ana", relationship = "Mae", phone = "119876543",
            email = null, receivesAlert = true, receivesReport = false
        )

        assertThat(saved).isFalse()
        verify(exactly = 0) { dbHelper.insertContact(any()) }
    }

    @Test
    fun `telefone com 10 digitos e aceito`() {
        val saved = viewModel.saveContact(
            existing = null, name = "Ana", relationship = "Mae", phone = "1132654321",
            email = null, receivesAlert = true, receivesReport = false
        )

        assertThat(saved).isTrue()
    }

    @Test
    fun `telefone formatado e aceito contando so os digitos`() {
        val saved = viewModel.saveContact(
            existing = null, name = "Ana", relationship = "Mae", phone = "(11) 98765-4321",
            email = null, receivesAlert = true, receivesReport = false
        )

        assertThat(saved).isTrue()
        verify { dbHelper.insertContact(match { it.phone == "(11) 98765-4321" }) }
    }

    @Test
    fun `email invalido e recusado quando preenchido`() {
        val saved = viewModel.saveContact(
            existing = null, name = "Ana", relationship = "Mae", phone = "11987654321",
            email = "nao-e-email", receivesAlert = true, receivesReport = false
        )

        assertThat(saved).isFalse()
        verify(exactly = 0) { dbHelper.insertContact(any()) }
    }

    @Test
    fun `email vazio e opcional`() {
        val saved = viewModel.saveContact(
            existing = null, name = "Ana", relationship = "Mae", phone = "11987654321",
            email = "", receivesAlert = true, receivesReport = false
        )

        assertThat(saved).isTrue()
    }

    @Test
    fun `receivesReport so pode ficar ligado com email presente`() {
        val saved = viewModel.saveContact(
            existing = null, name = "Ana", relationship = "Mae", phone = "11987654321",
            email = "", receivesAlert = true, receivesReport = true
        )

        assertThat(saved).isFalse()
        verify(exactly = 0) { dbHelper.insertContact(any()) }
    }

    @Test
    fun `receivesReport ligado com email presente e aceito`() {
        val saved = viewModel.saveContact(
            existing = null, name = "Ana", relationship = "Mae", phone = "11987654321",
            email = "ana@example.com", receivesAlert = true, receivesReport = true
        )

        assertThat(saved).isTrue()
    }

    // --- Persistence ---

    @Test
    fun `contato valido novo e gravado via insertContact`() {
        val saved = viewModel.saveContact(
            existing = null, name = "Ana", relationship = "Mae", phone = "11987654321",
            email = "ana@example.com", receivesAlert = true, receivesReport = true
        )

        assertThat(saved).isTrue()
        verify {
            dbHelper.insertContact(match {
                it.name == "Ana" && it.phone == "11987654321" &&
                    it.email == "ana@example.com" && !it.isPrimary
            })
        }
    }

    @Test
    fun `edicao de contato existente usa updateContact preservando id e isPrimary`() {
        val existing = sampleContact(id = 7, isPrimary = true)

        val saved = viewModel.saveContact(
            existing = existing, name = "Ana Nova", relationship = "Mae", phone = "11987654321",
            email = "ana@example.com", receivesAlert = true, receivesReport = true
        )

        assertThat(saved).isTrue()
        verify {
            dbHelper.updateContact(match { it.id == 7L && it.isPrimary && it.name == "Ana Nova" })
        }
        verify(exactly = 0) { dbHelper.insertContact(any()) }
    }

    // --- Removal ---

    @Test
    fun `remocao de contato comum funciona`() {
        val comum = sampleContact(id = 2, isPrimary = false)

        viewModel.removeContact(comum)

        verify { dbHelper.deleteContact(2) }
    }

    @Test
    fun `remocao do contato principal delega ao helper sem contornar a protecao`() {
        val principal = sampleContact(id = 1, isPrimary = true)

        viewModel.removeContact(principal)

        verify { dbHelper.deleteContact(1) }
        verify(exactly = 0) { dbHelper.updateContact(any()) }
    }

    // --- Alert/report switches ---

    @Test
    fun `alternar o switch de alerta persiste via updateContact`() {
        val alvo = sampleContact(id = 3, receivesAlert = false)

        viewModel.setReceivesAlert(alvo, true)

        verify { dbHelper.updateContact(match { it.id == 3L && it.receivesAlert }) }
    }

    @Test
    fun `alternar o switch de relatorio persiste via updateContact`() {
        val alvo = sampleContact(id = 4, receivesReport = false)

        viewModel.setReceivesReport(alvo, true)

        verify { dbHelper.updateContact(match { it.id == 4L && it.receivesReport }) }
    }

    // --- Defect A (RED) — a contact without an e-mail can never end up with
    // receivesReport = true, no matter which entry point flips the switch.
    //
    // Two paths reach persistence today: the dialog's swDlgReceivesReport (b20, guarded only
    // by ContactDialog's Save-button gate, which validateContact already covers) and the list
    // row's swReceivesReport (b19, ContactAdapter.kt:67-71 -> onReportToggled -> this function),
    // which writes straight to SQLite through updateContact with no validation at all. The
    // second path is the one that actually lets an invalid state reach the database.
    //
    // canReceiveReport() does not exist on SupportNetworkViewModel yet — GREEN adds it so
    // ContactDialog can also disable swDlgReceivesReport itself (not just gate Save) instead of
    // leaving it checked and enabled with an empty e-mail field, per the b20 hint text.
    // setReceivesReport() returning Unit does not exist as a guard point — GREEN changes its
    // signature to return Boolean and refuses to persist when enabling without an e-mail.

    @Test
    fun `canReceiveReport e falso sem email, vazio ou so espacos`() {
        assertThat(viewModel.canReceiveReport(null)).isFalse()
        assertThat(viewModel.canReceiveReport("")).isFalse()
        assertThat(viewModel.canReceiveReport("   ")).isFalse()
    }

    @Test
    fun `canReceiveReport e verdadeiro com email preenchido`() {
        assertThat(viewModel.canReceiveReport("ana@example.com")).isTrue()
    }

    @Test
    fun `setReceivesReport recusa ligar o relatorio para contato sem email e nao persiste`() {
        val semEmail = sampleContact(id = 5, email = null, receivesReport = false)

        val aceito = viewModel.setReceivesReport(semEmail, true)

        assertThat(aceito).isFalse()
        verify(exactly = 0) { dbHelper.updateContact(any()) }
        assertThat(viewModel.validationError.value).isNotNull()
    }

    @Test
    fun `setReceivesReport liga normalmente quando o contato tem email`() {
        val comEmail = sampleContact(id = 6, email = "ana@example.com", receivesReport = false)

        val aceito = viewModel.setReceivesReport(comEmail, true)

        assertThat(aceito).isTrue()
        verify { dbHelper.updateContact(match { it.id == 6L && it.receivesReport }) }
    }

    @Test
    fun `setReceivesReport sempre permite desligar mesmo sem email`() {
        val semEmail = sampleContact(id = 7, email = null, receivesReport = true)

        val aceito = viewModel.setReceivesReport(semEmail, false)

        assertThat(aceito).isTrue()
        verify { dbHelper.updateContact(match { it.id == 7L && !it.receivesReport }) }
    }

    // --- List ---

    @Test
    fun `lista exposta no estado vem de getContacts`() {
        val lista = listOf(sampleContact(id = 1), sampleContact(id = 2))
        every { dbHelper.getContacts() } returns lista
        val vm = SupportNetworkViewModel(dbHelper, reportStorage)

        assertThat(vm.contacts.value).isEqualTo(lista)
    }

    // --- 7-day summary reuses ReportStorage ---

    @Test
    fun `resumo de 7 dias reaproveita o ReportStorage existente, sem gerador proprio`() {
        val now = 1_700_000_000_000L
        every { reportStorage.buildReport(now) } returns "resumo mockado"

        viewModel.shareSummary(now)

        assertThat(viewModel.summaryText.getOrAwaitValue()).isEqualTo("resumo mockado")
        verify { reportStorage.buildReport(now) }
    }

    // --- Requirement 2 (Module 6) — who the 7-day summary SMS goes to ---
    //
    // SupportNetworkActivity currently calls UIHelper.sendSmsViaMessagingApp(this, "", text) —
    // an empty phone number, so the smsto: intent opens the contact picker instead of a
    // pre-addressed conversation. handoff-android.md §9 does not say which contact a manual
    // "share summary" tap should target when several are registered, so this suite adopts the
    // narrowest defensible rule: the primary contact, because it is the one row guaranteed to
    // exist and that GlicoKidsDbHelper.deleteContact refuses to remove (§9.1). getSummaryRecipientPhone()
    // does not exist on SupportNetworkViewModel yet — GREEN adds it and wires the Activity to use it.

    @Test
    fun `telefone do resumo de 7 dias e o do contato principal, nao o primeiro da lista`() {
        val outro = sampleContact(id = 2, phone = "11888880000", isPrimary = false)
        val principal = sampleContact(id = 1, phone = "11999990000", isPrimary = true)
        every { dbHelper.getContacts() } returns listOf(outro, principal)
        val vm = SupportNetworkViewModel(dbHelper, reportStorage)

        assertThat(vm.getSummaryRecipientPhone()).isEqualTo("11999990000")
    }

    @Test
    fun `sem contato principal cadastrado, resumo de 7 dias fica sem destinatario`() {
        every { dbHelper.getContacts() } returns emptyList()
        val vm = SupportNetworkViewModel(dbHelper, reportStorage)

        assertThat(vm.getSummaryRecipientPhone()).isNull()
    }

    // Onboarding (b3) never ran the primary-contact step yet (see TODO(onboarding) in
    // GlicoKidsDbHelper), so a network with contacts but none marked isPrimary is today's
    // reality, not an edge case — the summary still needs somewhere to go.
    @Test
    fun `sem contato principal mas com contatos cadastrados, resumo de 7 dias usa o primeiro da lista`() {
        val primeiro = sampleContact(id = 1, phone = "11888880000", isPrimary = false)
        val segundo = sampleContact(id = 2, phone = "11999990000", isPrimary = false)
        every { dbHelper.getContacts() } returns listOf(primeiro, segundo)
        val vm = SupportNetworkViewModel(dbHelper, reportStorage)

        assertThat(vm.getSummaryRecipientPhone()).isEqualTo("11888880000")
    }
}
