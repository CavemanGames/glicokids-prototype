package com.glicokids.prototype.data.local

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Módulo 5 — requisitos 3, 4 e 5: escrita com [FileOutputStream], leitura com
 * [FileInputStream] + [InputStreamReader] e cópia opcional para armazenamento externo.
 *
 * O relatório é montado a partir do SQLite (últimos 7 dias) e NUNCA leva o nome
 * completo da criança: só primeiro nome + iniciais (dado de saúde, LGPD).
 *
 * Tudo aqui toca disco: chame fora da main thread.
 */
@Singleton
class ReportStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbHelper: GlicoKidsDbHelper,
    private val prefs: AppPreferences
) {

    /** Arquivo interno do relatório; existe só depois do primeiro "Exportar". */
    private val internalFile: File
        get() = File(context.filesDir, REPORT_FILE_NAME)

    fun reportExists(): Boolean = internalFile.exists()

    /**
     * Requisito 3 — gera o relatório dos últimos 7 dias e grava com [FileOutputStream].
     * Devolve o conteúdo gravado.
     */
    fun generateAndSave(nowMillis: Long): String {
        val content = buildReport(nowMillis)
        FileOutputStream(internalFile).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
        prefs.lastReportAt = nowMillis
        return content
    }

    /**
     * Requisito 4 — lê o relatório com [FileInputStream] + [InputStreamReader] +
     * [BufferedReader]. Devolve null quando o arquivo ainda não existe.
     */
    fun readReport(): String? {
        if (!internalFile.exists()) return null
        return FileInputStream(internalFile).use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
        }
    }

    /**
     * Requisito 5 — cópia fora do sandbox do app. Função única e à prova de falha:
     * nunca propaga exceção, devolve o caminho usado ou null.
     *
     * Até a API 28 usa [Environment.getExternalStorageDirectory] (o método exigido
     * pelo módulo); da 29 em diante isso é bloqueado por scoped storage e caímos
     * em `getExternalFilesDir`, que não pede permissão.
     */
    fun saveReportExternally(content: String): String? {
        return try {
            val dir = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                Environment.getExternalStorageDirectory()
            } else {
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            } ?: return null

            if (!dir.exists() && !dir.mkdirs()) return null

            val target = File(dir, REPORT_FILE_NAME)
            FileOutputStream(target).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            target.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao salvar cópia externa do relatório", e)
            null
        }
    }

    // ------------------------------------------------------------------

    /** Primeiro nome por extenso + iniciais dos demais ("Lucas Silva Souza" -> "Lucas S. S."). */
    internal fun anonymizedName(fullName: String): String {
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "—"
        val initials = parts.drop(1).joinToString(" ") { "${it.first().uppercaseChar()}." }
        return if (initials.isBlank()) parts.first() else "${parts.first()} $initials"
    }

    internal fun buildReport(nowMillis: Long): String {
        val since = nowMillis - SEVEN_DAYS_MILLIS
        val readings = dbHelper.getGlucoseReadingsSince(since)
        val meals = dbHelper.getMealsSince(since)

        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        val short = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))

        return buildString {
            appendLine("GlicoKids — Relatório dos últimos 7 dias")
            appendLine("=".repeat(46))
            appendLine("Criança: ${anonymizedName(prefs.childName)}")
            appendLine("Faixa alvo vigente: ${prefs.rangeMin}–${prefs.rangeMax} mg/dL")
            appendLine("Gerado em: ${stamp.format(Date(nowMillis))}")
            appendLine()

            appendLine("GLICEMIAS (${readings.size} registro(s))")
            appendLine("-".repeat(46))
            if (readings.isEmpty()) {
                appendLine("Nenhuma leitura registrada no período.")
            } else {
                readings.forEach {
                    appendLine(
                        "${short.format(Date(it.createdAt))}  ${it.valueMgdl} mg/dL  " +
                            "${statusLabel(it.status)}  (${it.source.name.lowercase()})"
                    )
                }
            }
            appendLine()

            appendLine("REFEIÇÕES (${meals.size} registro(s))")
            appendLine("-".repeat(46))
            if (meals.isEmpty()) {
                appendLine("Nenhuma refeição registrada no período.")
            } else {
                meals.forEach {
                    appendLine(
                        "${short.format(Date(it.createdAt))}  ${it.label}  " +
                            "${formatNumber(it.carbsG)} g carbo  ${formatNumber(it.bolusUi)} UI"
                    )
                }
            }
            appendLine()

            appendLine("RESUMO")
            appendLine("-".repeat(46))
            val inRange = readings.count { it.status == UIHelper.GlucoseStatus.NA_META }
            val percent = if (readings.isEmpty()) 0 else (inRange * 100) / readings.size
            appendLine("Tempo na meta: $percent% ($inRange de ${readings.size})")
            appendLine("Total de carboidratos: ${formatNumber(meals.sumOf { it.carbsG })} g")
            appendLine("Total de bolus: ${formatNumber(meals.sumOf { it.bolusUi })} UI")
            appendLine()
            appendLine("Protótipo acadêmico — não substitui avaliação médica.")
        }
    }

    private fun statusLabel(status: UIHelper.GlucoseStatus) = when (status) {
        UIHelper.GlucoseStatus.NA_META -> "na meta"
        UIHelper.GlucoseStatus.ATENCAO -> "atenção"
        UIHelper.GlucoseStatus.FORA_DA_META -> "fora da meta"
    }

    private fun formatNumber(value: Double) =
        String.format(Locale("pt", "BR"), "%.1f", value)

    companion object {
        private const val TAG = "GlicoKids_Report"
        const val REPORT_FILE_NAME = "relatorio_glicokids.txt"
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
