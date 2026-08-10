package com.glicokids.prototype.data.local

import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.data.model.GlucoseReading
import com.glicokids.prototype.data.model.MealEntry
import com.glicokids.prototype.domain.model.ReadingSource
import com.glicokids.prototype.util.UIHelper
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlicoKidsDbHelperTest {

    private lateinit var dbHelper: GlicoKidsDbHelper

    private val now = System.currentTimeMillis()
    private val umDia = 24L * 60 * 60 * 1000

    @Before
    fun setup() {
        dbHelper = GlicoKidsDbHelper(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    @Test
    fun `semeia a tabela foods a partir de raw alimentos json`() {
        val foods = dbHelper.getFoods()

        assertThat(foods).isNotEmpty()
        assertThat(foods.map { it.nome }).contains("Arroz branco cozido")
        assertThat(foods.all { it.porcao.isNotBlank() }).isTrue()
    }

    @Test
    fun `semeia as 6 medalhas do design, 4 desbloqueadas e 2 bloqueadas`() {
        val medals = dbHelper.getMedals()

        assertThat(medals).hasSize(6)
        assertThat(medals.count { it.unlocked }).isEqualTo(4)
        assertThat(medals.count { !it.unlocked }).isEqualTo(2)
        assertThat(medals.map { it.rarity }.toSet())
            .containsExactly("OURO", "ESMERALDA", "DIAMANTE")
    }

    @Test
    fun `grava e devolve leituras de glicemia dentro da janela de 7 dias`() {
        dbHelper.insertGlucoseReading(reading(250, now))
        dbHelper.insertGlucoseReading(reading(110, now - 2 * umDia))
        dbHelper.insertGlucoseReading(reading(99, now - 30 * umDia)) // fora da janela

        val recentes = dbHelper.getGlucoseReadingsSince(now - 7 * umDia)

        assertThat(recentes).hasSize(2)
        assertThat(recentes.map { it.valueMgdl }).containsExactly(110, 250).inOrder()
    }

    @Test
    fun `preserva o status e a origem da leitura`() {
        dbHelper.insertGlucoseReading(
            GlucoseReading(
                valueMgdl = 250,
                status = UIHelper.GlucoseStatus.FORA_DA_META,
                source = ReadingSource.SENSOR,
                createdAt = now
            )
        )

        val salva = dbHelper.getGlucoseReadingsSince(0).single()

        assertThat(salva.status).isEqualTo(UIHelper.GlucoseStatus.FORA_DA_META)
        assertThat(salva.source).isEqualTo(ReadingSource.SENSOR)
    }

    @Test
    fun `grava refeicao e devolve as mais recentes primeiro`() {
        dbHelper.insertMeal(meal("Café", 30.0, now - 3 * umDia))
        dbHelper.insertMeal(meal("Almoço", 45.0, now))

        val recentes = dbHelper.getRecentMeals(5)

        assertThat(recentes.map { it.label }).containsExactly("Almoço", "Café").inOrder()
        assertThat(recentes.first().carbsG).isEqualTo(45.0)
        assertThat(recentes.first().bolusUi).isEqualTo(3.5)
    }

    @Test
    fun `refeicao sem foto aceita photo_path nulo`() {
        dbHelper.insertMeal(meal("Jantar", 20.0, now))

        assertThat(dbHelper.getRecentMeals(1).single().photoPath).isNull()
    }

    // ------------------------------------------------------------------
    // Module 6 — contacts (support network, schema v2)
    // ------------------------------------------------------------------

    @Test
    fun `getAlertRecipients returns only contacts subscribed to alerts`() {
        dbHelper.insertContact(contact(name = "Ana", isPrimary = true, receivesAlert = true))
        dbHelper.insertContact(contact(name = "Beto", receivesAlert = false))
        dbHelper.insertContact(contact(name = "Carla", receivesAlert = true))

        val recipients = dbHelper.getAlertRecipients()

        assertThat(recipients.map { it.name }).containsExactly("Ana", "Carla")
    }

    @Test
    fun `deleteContact silently refuses to remove the primary contact`() {
        val primaryId = dbHelper.insertContact(contact(name = "Ana", isPrimary = true))

        dbHelper.deleteContact(primaryId)

        assertThat(dbHelper.getContacts().map { it.name }).contains("Ana")
    }

    @Test
    fun `deleteContact removes a non primary contact`() {
        val id = dbHelper.insertContact(contact(name = "Beto", isPrimary = false))

        dbHelper.deleteContact(id)

        assertThat(dbHelper.getContacts().map { it.name }).doesNotContain("Beto")
    }

    @Test
    fun `findContactByPhone matches the formatted, plain digits and country code variants`() {
        dbHelper.insertContact(contact(name = "Ana", phone = "(11) 98877-6543"))

        assertThat(dbHelper.findContactByPhone("(11) 98877-6543")?.name).isEqualTo("Ana")
        assertThat(dbHelper.findContactByPhone("11988776543")?.name).isEqualTo("Ana")
        assertThat(dbHelper.findContactByPhone("+5511988776543")?.name).isEqualTo("Ana")
    }

    @Test
    fun `findContactByPhone returns null when no contact matches`() {
        dbHelper.insertContact(contact(name = "Ana", phone = "(11) 98877-6543"))

        assertThat(dbHelper.findContactByPhone("11900000000")).isNull()
    }

    private fun contact(
        name: String,
        relationship: String = "Mãe",
        phone: String = "(11) 98877-6543",
        receivesAlert: Boolean = true,
        receivesReport: Boolean = false,
        isPrimary: Boolean = false
    ) = Contact(
        name = name,
        relationship = relationship,
        phone = phone,
        email = null,
        receivesAlert = receivesAlert,
        receivesReport = receivesReport,
        isPrimary = isPrimary,
        createdAt = now
    )

    private fun reading(value: Int, at: Long) = GlucoseReading(
        valueMgdl = value,
        status = UIHelper.glucoseStatus(value, 70, 180),
        source = ReadingSource.MANUAL,
        createdAt = at
    )

    private fun meal(label: String, carbs: Double, at: Long) = MealEntry(
        label = label,
        carbsG = carbs,
        glucoseMgdl = 110,
        bolusUi = 3.5,
        photoPath = null,
        createdAt = at
    )
}
