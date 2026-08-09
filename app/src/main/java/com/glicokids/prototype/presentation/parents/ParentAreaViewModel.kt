package com.glicokids.prototype.presentation.parents

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.local.ReportStorage
import com.glicokids.prototype.data.model.MealEntry
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ParentAreaViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val dbHelper: GlicoKidsDbHelper,
    private val reportStorage: ReportStorage
) : ViewModel() {

    /** Clinical parameters coming from SharedPreferences (§8.1). */
    data class ClinicalParams(
        val isf: Int,
        val icRatio: Int,
        val targetGlucose: Int,
        val rangeMin: Int,
        val rangeMax: Int,
        val maxDose: Int
    )

    /** One bar of the 7-day chart: the daily average plus its status under the active range. */
    data class DayBar(
        val label: String,
        val average: Int,
        val status: UIHelper.GlucoseStatus,
        val hasData: Boolean
    )

    private val _params = MutableLiveData<ClinicalParams>()
    val params: LiveData<ClinicalParams> = _params

    private val _targetRange = MutableLiveData<Pair<Int, Int>>()
    val targetRange: LiveData<Pair<Int, Int>> = _targetRange

    private val _weekBars = MutableLiveData<List<DayBar>>()
    val weekBars: LiveData<List<DayBar>> = _weekBars

    /** Percentage of in-range readings over the last 7 days. */
    private val _timeInRange = MutableLiveData<Int>()
    val timeInRange: LiveData<Int> = _timeInRange

    private val _meals = MutableLiveData<List<MealEntry>>()
    val meals: LiveData<List<MealEntry>> = _meals

    private val _lastReportAt = MutableLiveData<Long>()
    val lastReportAt: LiveData<Long> = _lastReportAt

    private val _validationError = MutableLiveData<String?>(null)
    val validationError: LiveData<String?> = _validationError

    init {
        publishParams()
        _lastReportAt.value = prefs.lastReportAt
    }

    /** Reloads from SQLite off the main thread (I/O never runs on the UI). */
    fun refresh(nowMillis: Long = System.currentTimeMillis()) {
        publishParams()
        _lastReportAt.value = prefs.lastReportAt

        viewModelScope.launch {
            val since = nowMillis - ReportStorage.SEVEN_DAYS_MILLIS
            val readings = withContext(Dispatchers.IO) { dbHelper.getGlucoseReadingsSince(since) }
            val recentMeals = withContext(Dispatchers.IO) { dbHelper.getRecentMeals(RECENT_MEALS_LIMIT) }

            _weekBars.value = buildWeekBars(readings.map { it.createdAt to it.valueMgdl }, nowMillis)
            _timeInRange.value = if (readings.isEmpty()) 0 else {
                readings.count { it.status == UIHelper.GlucoseStatus.NA_META } * 100 / readings.size
            }
            _meals.value = recentMeals
        }
    }

    private fun publishParams() {
        _params.value = ClinicalParams(
            isf = prefs.isf,
            icRatio = prefs.icRatio,
            targetGlucose = prefs.targetGlucose,
            rangeMin = prefs.rangeMin,
            rangeMax = prefs.rangeMax,
            maxDose = prefs.maxDose
        )
        _targetRange.value = prefs.rangeMin to prefs.rangeMax
    }

    /**
     * Buckets the readings into 7 days and classifies each one under the active range —
     * the bar colour comes from [UIHelper.glucoseStatus], never from a fixed threshold.
     */
    internal fun buildWeekBars(readings: List<Pair<Long, Int>>, nowMillis: Long): List<DayBar> {
        val dayFormat = SimpleDateFormat("EEE", Locale("pt", "BR"))
        val cal = Calendar.getInstance()
        val min = prefs.rangeMin
        val max = prefs.rangeMax

        return (6 downTo 0).map { daysAgo ->
            val dayStart = startOfDay(nowMillis - daysAgo * DAY_MILLIS)
            val dayEnd = dayStart + DAY_MILLIS
            val ofDay = readings.filter { it.first in dayStart until dayEnd }.map { it.second }

            cal.timeInMillis = dayStart
            val label = dayFormat.format(cal.time).take(3).replaceFirstChar { it.uppercase() }

            if (ofDay.isEmpty()) {
                DayBar(label, 0, UIHelper.GlucoseStatus.NA_META, hasData = false)
            } else {
                val average = ofDay.average().toInt()
                DayBar(label, average, UIHelper.glucoseStatus(average, min, max), hasData = true)
            }
        }
    }

    private fun startOfDay(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ------------------------------------------------------------------
    // Parameter editing (b18 — always through a validated dialog, never inline)
    // ------------------------------------------------------------------

    /** Rejects the new range unless it fits inside the accepted absolute bounds and min < max. */
    fun updateTargetRange(min: Int, max: Int): Boolean {
        if (min < AppPreferences.RANGE_ABSOLUTE_MIN ||
            max > AppPreferences.RANGE_ABSOLUTE_MAX ||
            min >= max
        ) {
            _validationError.value = "Faixa inválida"
            return false
        }

        prefs.saveTargetRange(min, max)
        _validationError.value = null
        publishParams()
        return true
    }

    /** Stores a simple clinical parameter; returns false when it falls outside the accepted range. */
    fun updateParam(param: Param, value: Int): Boolean {
        if (value !in param.range) {
            _validationError.value = "Valor fora do intervalo aceito"
            return false
        }
        when (param) {
            Param.ISF -> prefs.isf = value
            Param.IC_RATIO -> prefs.icRatio = value
            Param.TARGET -> prefs.targetGlucose = value
            Param.MAX_DOSE -> prefs.maxDose = value
        }
        _validationError.value = null
        publishParams()
        return true
    }

    enum class Param(val range: IntRange) {
        ISF(AppPreferences.ISF_ABSOLUTE_MIN..AppPreferences.ISF_ABSOLUTE_MAX),
        IC_RATIO(AppPreferences.IC_RATIO_ABSOLUTE_MIN..AppPreferences.IC_RATIO_ABSOLUTE_MAX),
        TARGET(AppPreferences.TARGET_GLUCOSE_ABSOLUTE_MIN..AppPreferences.TARGET_GLUCOSE_ABSOLUTE_MAX),
        MAX_DOSE(AppPreferences.MAX_DOSE_ABSOLUTE_MIN..AppPreferences.MAX_DOSE_ABSOLUTE_MAX)
    }

    // ------------------------------------------------------------------
    // Report (Module 5 — requirements 3, 4 and 5)
    // ------------------------------------------------------------------

    /** Requirement 3 — builds and writes it with FileOutputStream; returns the content. */
    fun exportReport(nowMillis: Long = System.currentTimeMillis(), onDone: (String) -> Unit) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) { reportStorage.generateAndSave(nowMillis) }
            _lastReportAt.value = prefs.lastReportAt
            onDone(content)
        }
    }

    /** Requirement 4 — reads it with FileInputStream + InputStreamReader; null if absent. */
    fun readReport(onDone: (String?) -> Unit) {
        viewModelScope.launch {
            onDone(withContext(Dispatchers.IO) { reportStorage.readReport() })
        }
    }

    /** Requirement 5 — copy outside the sandbox; returns the path used or null. */
    fun exportReportExternally(nowMillis: Long = System.currentTimeMillis(), onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                val content = reportStorage.readReport() ?: reportStorage.generateAndSave(nowMillis)
                reportStorage.saveReportExternally(content)
            }
            _lastReportAt.value = prefs.lastReportAt
            onDone(path)
        }
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val RECENT_MEALS_LIMIT = 5
    }
}
