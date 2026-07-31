package com.glicokids.prototype.presentation.parents

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.local.ReportStorage
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
}
