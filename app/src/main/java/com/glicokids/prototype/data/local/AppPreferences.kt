package com.glicokids.prototype.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Módulo 5 — requisitos 1 e 2: `SharedPreferences` comum, compartilhada entre Activities.
 *
 * Dono das preferências e dos parâmetros clínicos NÃO sensíveis (handoff §8.1).
 * O `parent_pin` NÃO mora aqui: é o único dado sensível e fica no
 * [EncryptedStorage] (EncryptedSharedPreferences).
 *
 * Os padrões vivem só neste arquivo — nenhuma tela repete 70, 180 ou 100.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Perfil da criança ---
    var childName: String
        get() = prefs.getString(KEY_CHILD_NAME, DEFAULT_CHILD_NAME) ?: DEFAULT_CHILD_NAME
        set(value) = prefs.edit().putString(KEY_CHILD_NAME, value).apply()

    var avatarIndex: Int
        get() = prefs.getInt(KEY_AVATAR_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_AVATAR_INDEX, value).apply()

    // --- Parâmetros clínicos (editáveis na Área dos Pais) ---
    var rangeMin: Int
        get() = prefs.getInt(KEY_RANGE_MIN, DEFAULT_RANGE_MIN)
        set(value) = prefs.edit().putInt(KEY_RANGE_MIN, value).apply()

    var rangeMax: Int
        get() = prefs.getInt(KEY_RANGE_MAX, DEFAULT_RANGE_MAX)
        set(value) = prefs.edit().putInt(KEY_RANGE_MAX, value).apply()

    var targetGlucose: Int
        get() = prefs.getInt(KEY_TARGET_GLUCOSE, DEFAULT_TARGET_GLUCOSE)
        set(value) = prefs.edit().putInt(KEY_TARGET_GLUCOSE, value).apply()

    /** Fator de sensibilidade à insulina (FSI). */
    var isf: Int
        get() = prefs.getInt(KEY_ISF, DEFAULT_ISF)
        set(value) = prefs.edit().putInt(KEY_ISF, value).apply()

    /** Relação insulina/carboidrato (I/C). */
    var icRatio: Int
        get() = prefs.getInt(KEY_IC_RATIO, DEFAULT_IC_RATIO)
        set(value) = prefs.edit().putInt(KEY_IC_RATIO, value).apply()

    /** Trava de dose máxima, em unidades de insulina. */
    var maxDose: Int
        get() = prefs.getInt(KEY_MAX_DOSE, DEFAULT_MAX_DOSE)
        set(value) = prefs.edit().putInt(KEY_MAX_DOSE, value).apply()

    // --- Gamificação ---
    var xp: Int
        get() = prefs.getInt(KEY_XP, 0)
        set(value) = prefs.edit().putInt(KEY_XP, value).apply()

    var coins: Int
        get() = prefs.getInt(KEY_COINS, 0)
        set(value) = prefs.edit().putInt(KEY_COINS, value).apply()

    var streak: Int
        get() = prefs.getInt(KEY_STREAK, 0)
        set(value) = prefs.edit().putInt(KEY_STREAK, value).apply()

    // --- Estado do app ---
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /** Epoch millis da última geração do relatório; 0 = nunca gerado. */
    var lastReportAt: Long
        get() = prefs.getLong(KEY_LAST_REPORT_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REPORT_AT, value).apply()

    /** Grava a faixa alvo em uma única transação — as duas pontas mudam juntas. */
    fun saveTargetRange(min: Int, max: Int) {
        prefs.edit()
            .putInt(KEY_RANGE_MIN, min)
            .putInt(KEY_RANGE_MAX, max)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "glicokids_prefs"

        const val KEY_CHILD_NAME = "child_name"
        const val KEY_AVATAR_INDEX = "avatar_index"
        const val KEY_RANGE_MIN = "range_min"
        const val KEY_RANGE_MAX = "range_max"
        const val KEY_TARGET_GLUCOSE = "target_glucose"
        const val KEY_ISF = "isf"
        const val KEY_IC_RATIO = "ic_ratio"
        const val KEY_MAX_DOSE = "max_dose"
        const val KEY_XP = "xp"
        const val KEY_COINS = "coins"
        const val KEY_STREAK = "streak"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_LAST_REPORT_AT = "last_report_at"

        const val DEFAULT_CHILD_NAME = "Lucas"
        const val DEFAULT_RANGE_MIN = 70
        const val DEFAULT_RANGE_MAX = 180
        const val DEFAULT_TARGET_GLUCOSE = 100
        const val DEFAULT_ISF = 50
        const val DEFAULT_IC_RATIO = 15
        const val DEFAULT_MAX_DOSE = 6

        /** Limites aceitos ao editar a faixa alvo (handoff §8). */
        const val RANGE_ABSOLUTE_MIN = 40
        const val RANGE_ABSOLUTE_MAX = 300
    }
}
