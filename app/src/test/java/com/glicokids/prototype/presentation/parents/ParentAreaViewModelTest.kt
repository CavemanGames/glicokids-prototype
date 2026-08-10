package com.glicokids.prototype.presentation.parents

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.local.ReportStorage
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.util.UIHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ParentAreaViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val prefs = mockk<AppPreferences>(relaxed = true)
    private val dbHelper = mockk<GlicoKidsDbHelper>(relaxed = true)
    private val reportStorage = mockk<ReportStorage>(relaxed = true)
    private lateinit var viewModel: ParentAreaViewModel

    @Before
    fun setup() {
        every { prefs.rangeMin } returns 70
        every { prefs.rangeMax } returns 180
        every { prefs.targetGlucose } returns 100
        every { prefs.isf } returns 50
        every { prefs.icRatio } returns 15
        every { prefs.maxDose } returns 6
        every { prefs.lastReportAt } returns 0L
        viewModel = ParentAreaViewModel(prefs, dbHelper, reportStorage)
    }

    @Test
    fun `publica os parametros clinicos vindos das SharedPreferences`() {
        val p = viewModel.params.value!!

        assertThat(p.isf).isEqualTo(50)
        assertThat(p.icRatio).isEqualTo(15)
        assertThat(p.targetGlucose).isEqualTo(100)
        assertThat(p.rangeMin).isEqualTo(70)
        assertThat(p.rangeMax).isEqualTo(180)
        assertThat(p.maxDose).isEqualTo(6)
    }

    @Test
    fun `faixa valida e gravada em uma unica transacao`() {
        assertThat(viewModel.updateTargetRange(90, 200)).isTrue()

        verify { prefs.saveTargetRange(90, 200) }
        assertThat(viewModel.validationError.value).isNull()
    }

    @Test
    fun `faixa fora dos limites absolutos e recusada`() {
        assertThat(viewModel.updateTargetRange(30, 200)).isFalse()
        assertThat(viewModel.updateTargetRange(90, 320)).isFalse()

        verify(exactly = 0) { prefs.saveTargetRange(any(), any()) }
        assertThat(viewModel.validationError.value).isNotNull()
    }

    @Test
    fun `minimo maior ou igual ao maximo e recusado`() {
        assertThat(viewModel.updateTargetRange(180, 180)).isFalse()
        assertThat(viewModel.updateTargetRange(190, 180)).isFalse()

        verify(exactly = 0) { prefs.saveTargetRange(any(), any()) }
    }

    @Test
    fun `parametro dentro do intervalo e persistido`() {
        assertThat(viewModel.updateParam(ParentAreaViewModel.Param.ISF, 40)).isTrue()

        verify { prefs.isf = 40 }
    }

    @Test
    fun `parametro fora do intervalo nao e persistido`() {
        assertThat(viewModel.updateParam(ParentAreaViewModel.Param.MAX_DOSE, 99)).isFalse()

        verify(exactly = 0) { prefs.maxDose = any() }
        assertThat(viewModel.validationError.value).isNotNull()
    }

    @Test
    fun `grafico devolve sempre 7 dias, marcando os dias sem leitura`() {
        val now = 1_700_000_000_000L
        val bars = viewModel.buildWeekBars(listOf(now to 110), now)

        assertThat(bars).hasSize(7)
        assertThat(bars.count { it.hasData }).isEqualTo(1)
        assertThat(bars.last().hasData).isTrue()
        assertThat(bars.last().average).isEqualTo(110)
    }

    @Test
    fun `cor da barra segue a faixa alvo configurada, nao 70-180 fixo`() {
        val now = 1_700_000_000_000L

        val comFaixaPadrao = viewModel.buildWeekBars(listOf(now to 190), now).last()
        assertThat(comFaixaPadrao.status).isEqualTo(UIHelper.GlucoseStatus.FORA_DA_META)

        every { prefs.rangeMin } returns 90
        every { prefs.rangeMax } returns 220
        val comFaixaAmpliada = viewModel.buildWeekBars(listOf(now to 190), now).last()
        assertThat(comFaixaAmpliada.status).isEqualTo(UIHelper.GlucoseStatus.NA_META)
    }

    // --- Requirement 4 (Module 6) — who the e-mail report goes to ---
    //
    // btnEmailReport exists in fragment_parent_area.xml (line 216) but ParentAreaFragment never
    // registers a click listener on it — tapping it does nothing. handoff-android.md §9.4 row 4
    // ties the e-mail requirement explicitly to "destinatários com receives_report=1", so this
    // suite pins that rule down as the ViewModel-owned selection logic GREEN needs to wire the
    // button to. getReportRecipients() does not exist on ParentAreaViewModel yet.

    private fun sampleContact(
        id: Long = 1,
        email: String? = "contato$id@example.com",
        receivesReport: Boolean = false
    ) = Contact(
        id = id,
        name = "Contato $id",
        relationship = "Mae",
        phone = "11987654321",
        email = email,
        receivesAlert = true,
        receivesReport = receivesReport,
        isPrimary = false,
        createdAt = 0L
    )

    @Test
    fun `destinatarios do relatorio por email sao os contatos com receivesReport ligado`() {
        val comRelatorio = sampleContact(id = 1, email = "ana@example.com", receivesReport = true)
        val semRelatorio = sampleContact(id = 2, email = "beto@example.com", receivesReport = false)
        every { dbHelper.getContacts() } returns listOf(comRelatorio, semRelatorio)

        assertThat(viewModel.getReportRecipients()).containsExactly("ana@example.com")
    }

    @Test
    fun `contato com receivesReport ligado mas sem email fica de fora dos destinatarios`() {
        val semEmail = sampleContact(id = 1, email = null, receivesReport = true)
        every { dbHelper.getContacts() } returns listOf(semEmail)

        assertThat(viewModel.getReportRecipients()).isEmpty()
    }

    @Test
    fun `nenhum contato configurado para relatorio devolve lista vazia de destinatarios`() {
        every { dbHelper.getContacts() } returns emptyList()

        assertThat(viewModel.getReportRecipients()).isEmpty()
    }

    @Test
    fun `varios contatos com receivesReport ligado entram todos nos destinatarios`() {
        val ana = sampleContact(id = 1, email = "ana@example.com", receivesReport = true)
        val beto = sampleContact(id = 2, email = "beto@example.com", receivesReport = true)
        val carla = sampleContact(id = 3, email = "carla@example.com", receivesReport = false)
        every { dbHelper.getContacts() } returns listOf(ana, beto, carla)

        assertThat(viewModel.getReportRecipients()).containsExactly("ana@example.com", "beto@example.com")
    }

    // --- Defect C (RED) — the support-network summary card must reflect the database ---
    //
    // tvSupportNetworkSummary (fragment_parent_area.xml:249) is a hardcoded XML string
    // ("Nenhum contato além do responsável") that ParentAreaFragment never binds to anything.
    // Onboarding (b3) never ran (TODO(onboarding) in GlicoKidsDbHelper), so is_primary=0 on
    // every row is today's reality, not an edge case — the text must stay true whether or not
    // a primary contact exists. handoff-android.md does not define the exact wording for this
    // card, so this suite pins down a literal proposal for GREEN; produto/human should confirm
    // the final copy before it ships. buildSupportNetworkSummary() does not exist on
    // ParentAreaViewModel yet.

    private fun contactFor(id: Long, name: String, isPrimary: Boolean) = Contact(
        id = id,
        name = name,
        relationship = "Mae",
        phone = "11987654321",
        email = "c$id@example.com",
        receivesAlert = true,
        receivesReport = false,
        isPrimary = isPrimary,
        createdAt = 0L
    )

    @Test
    fun `resumo da rede de apoio sem nenhum contato nao menciona responsavel`() {
        assertThat(viewModel.buildSupportNetworkSummary(emptyList())).isEqualTo("Nenhum contato cadastrado")
    }

    @Test
    fun `resumo da rede de apoio com um contato e nenhum principal nao menciona responsavel`() {
        val contatos = listOf(contactFor(1, "Ana", isPrimary = false))

        assertThat(viewModel.buildSupportNetworkSummary(contatos)).isEqualTo("1 contato cadastrado")
    }

    @Test
    fun `resumo da rede de apoio pluraliza quando ha mais de um contato sem principal`() {
        val contatos = listOf(
            contactFor(1, "Ana", isPrimary = false),
            contactFor(2, "Beto", isPrimary = false)
        )

        assertThat(viewModel.buildSupportNetworkSummary(contatos)).isEqualTo("2 contatos cadastrados")
    }

    @Test
    fun `resumo da rede de apoio mostra o nome do responsavel quando ha contato principal`() {
        val contatos = listOf(contactFor(1, "Ana", isPrimary = true))

        assertThat(viewModel.buildSupportNetworkSummary(contatos)).isEqualTo("Responsável: Ana")
    }

    @Test
    fun `resumo da rede de apoio soma os demais contatos ao lado do responsavel`() {
        val contatos = listOf(
            contactFor(1, "Ana", isPrimary = true),
            contactFor(2, "Beto", isPrimary = false),
            contactFor(3, "Carla", isPrimary = false)
        )

        assertThat(viewModel.buildSupportNetworkSummary(contatos)).isEqualTo("Responsável: Ana · +2 contatos")
    }
}
