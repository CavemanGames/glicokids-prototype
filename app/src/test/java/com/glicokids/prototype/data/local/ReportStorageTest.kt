package com.glicokids.prototype.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.data.model.GlucoseReading
import com.glicokids.prototype.data.model.MealEntry
import com.glicokids.prototype.domain.model.ReadingSource
import com.glicokids.prototype.util.UIHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ReportStorageTest {

    private lateinit var context: Context
    private lateinit var dbHelper: GlicoKidsDbHelper
    private val prefs = mockk<AppPreferences>(relaxed = true)
    private lateinit var storage: ReportStorage

    private val now = System.currentTimeMillis()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbHelper = GlicoKidsDbHelper(context)

        every { prefs.childName } returns "Lucas Silva Souza"
        every { prefs.rangeMin } returns 70
        every { prefs.rangeMax } returns 180
        every { prefs.lastReportAt } returns 0L

        storage = ReportStorage(context, dbHelper, prefs)
        File(context.filesDir, ReportStorage.REPORT_FILE_NAME).delete()
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    @Test
    fun `sem relatorio gerado, a leitura devolve null`() {
        assertThat(storage.reportExists()).isFalse()
        assertThat(storage.readReport()).isNull()
    }

    @Test
    fun `grava com FileOutputStream e le de volta o mesmo conteudo`() {
        val gravado = storage.generateAndSave(now)

        assertThat(storage.reportExists()).isTrue()
        assertThat(storage.readReport()).isEqualTo(gravado)
    }

    @Test
    fun `o relatorio nunca leva o nome completo da crianca`() {
        val conteudo = storage.generateAndSave(now)

        assertThat(conteudo).doesNotContain("Lucas Silva Souza")
        assertThat(conteudo).contains("Lucas S. S.")
    }

    @Test
    fun `anonimiza mantendo o primeiro nome e as iniciais do resto`() {
        assertThat(storage.anonymizedName("Lucas Silva Souza")).isEqualTo("Lucas S. S.")
        assertThat(storage.anonymizedName("Lucas")).isEqualTo("Lucas")
        assertThat(storage.anonymizedName("   ")).isEqualTo("—")
    }

    @Test
    fun `o relatorio traz a faixa alvo vigente, nao um valor fixo`() {
        every { prefs.rangeMin } returns 90
        every { prefs.rangeMax } returns 200

        assertThat(storage.buildReport(now)).contains("90–200 mg/dL")
    }

    @Test
    fun `resume so os ultimos 7 dias e calcula o tempo na meta`() {
        val umDia = 24L * 60 * 60 * 1000
        dbHelper.insertGlucoseReading(reading(110, now))            // na meta
        dbHelper.insertGlucoseReading(reading(250, now - umDia))    // fora
        dbHelper.insertGlucoseReading(reading(120, now - 30 * umDia)) // fora da janela
        dbHelper.insertMeal(
            MealEntry(
                label = "Almoço", carbsG = 45.0, glucoseMgdl = 110,
                bolusUi = 3.5, photoPath = null, createdAt = now
            )
        )

        val conteudo = storage.buildReport(now)

        assertThat(conteudo).contains("GLICEMIAS (2 registro(s))")
        assertThat(conteudo).contains("REFEIÇÕES (1 registro(s))")
        assertThat(conteudo).contains("Tempo na meta: 50%")
    }

    @Test
    fun `salvar copia externa devolve um caminho e nunca lanca excecao`() {
        val caminho = storage.saveReportExternally("conteúdo de teste")

        assertThat(caminho).isNotNull()
        assertThat(File(caminho!!).readText()).isEqualTo("conteúdo de teste")
    }

    private fun reading(value: Int, at: Long) = GlucoseReading(
        valueMgdl = value,
        status = UIHelper.glucoseStatus(value, 70, 180),
        source = ReadingSource.MANUAL,
        createdAt = at
    )
}
