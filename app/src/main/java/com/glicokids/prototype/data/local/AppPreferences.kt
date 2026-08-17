package com.glicokids.prototype.data.local

import android.content.Context
import android.content.SharedPreferences
import com.glicokids.prototype.domain.model.AlertMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module 5 — requirements 1 and 2: plain `SharedPreferences`, shared across Activities.
 *
 * Owns the non-sensitive settings and clinical parameters: child profile,
 * glycemia target range, insulin sensitivity factor, insulin-to-carb ratio,
 * dose lock and alert mode/throttle.
 * `parent_pin` does NOT live here: it is the only sensitive value and stays in
 * [EncryptedStorage] (EncryptedSharedPreferences).
 *
 * Defaults live in this file alone — no screen repeats 70, 180 or 100.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Child profile ---
    var childName: String
        get() = prefs.getString(KEY_CHILD_NAME, DEFAULT_CHILD_NAME) ?: DEFAULT_CHILD_NAME
        set(value) = prefs.edit().putString(KEY_CHILD_NAME, value).apply()

    var avatarIndex: Int
        get() = prefs.getInt(KEY_AVATAR_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_AVATAR_INDEX, value).apply()

    // --- Clinical parameters (editable in the Parent Area) ---
    var rangeMin: Int
        get() = prefs.getInt(KEY_RANGE_MIN, DEFAULT_RANGE_MIN)
        set(value) = prefs.edit().putInt(KEY_RANGE_MIN, value).apply()

    var rangeMax: Int
        get() = prefs.getInt(KEY_RANGE_MAX, DEFAULT_RANGE_MAX)
        set(value) = prefs.edit().putInt(KEY_RANGE_MAX, value).apply()

    var targetGlucose: Int
        get() = prefs.getInt(KEY_TARGET_GLUCOSE, DEFAULT_TARGET_GLUCOSE)
        set(value) = prefs.edit().putInt(KEY_TARGET_GLUCOSE, value).apply()

    /** Insulin sensitivity factor (ISF). */
    var isf: Int
        get() = prefs.getInt(KEY_ISF, DEFAULT_ISF)
        set(value) = prefs.edit().putInt(KEY_ISF, value).apply()

    /** Insulin-to-carbohydrate ratio (I/C). */
    var icRatio: Int
        get() = prefs.getInt(KEY_IC_RATIO, DEFAULT_IC_RATIO)
        set(value) = prefs.edit().putInt(KEY_IC_RATIO, value).apply()

    /** Maximum dose lock, in insulin units. */
    var maxDose: Int
        get() = prefs.getInt(KEY_MAX_DOSE, DEFAULT_MAX_DOSE)
        set(value) = prefs.edit().putInt(KEY_MAX_DOSE, value).apply()

    // --- Gamification ---
    var xp: Int
        get() = prefs.getInt(KEY_XP, 0)
        set(value) = prefs.edit().putInt(KEY_XP, value).apply()

    var coins: Int
        get() = prefs.getInt(KEY_COINS, 0)
        set(value) = prefs.edit().putInt(KEY_COINS, value).apply()

    var streak: Int
        get() = prefs.getInt(KEY_STREAK, 0)
        set(value) = prefs.edit().putInt(KEY_STREAK, value).apply()

    // --- App state ---
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /** Epoch millis of the last report generation; 0 = never generated. */
    var lastReportAt: Long
        get() = prefs.getLong(KEY_LAST_REPORT_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REPORT_AT, value).apply()

    // --- Alert prefs (Module 6) ---
    // Hypo and hyper are independent decisions — never a single switch for both.
    var alertModeHypo: AlertMode
        get() = runCatching { AlertMode.valueOf(prefs.getString(KEY_ALERT_MODE_HYPO, null) ?: "") }
            .getOrDefault(DEFAULT_ALERT_MODE_HYPO)
        set(value) = prefs.edit().putString(KEY_ALERT_MODE_HYPO, value.name).apply()

    var alertModeHyper: AlertMode
        get() = runCatching { AlertMode.valueOf(prefs.getString(KEY_ALERT_MODE_HYPER, null) ?: "") }
            .getOrDefault(DEFAULT_ALERT_MODE_HYPER)
        set(value) = prefs.edit().putString(KEY_ALERT_MODE_HYPER, value.name).apply()

    /** Minimum interval, in minutes, between two automatic alerts. */
    var alertThrottleMin: Int
        get() = prefs.getInt(KEY_ALERT_THROTTLE_MIN, DEFAULT_ALERT_THROTTLE_MIN)
        set(value) = prefs.edit().putInt(KEY_ALERT_THROTTLE_MIN, value).apply()

    /** Whether a return to the target range also triggers a notice. */
    var alertOnRecovery: Boolean
        get() = prefs.getBoolean(KEY_ALERT_ON_RECOVERY, DEFAULT_ALERT_ON_RECOVERY)
        set(value) = prefs.edit().putBoolean(KEY_ALERT_ON_RECOVERY, value).apply()

    /** Epoch millis of the last alert sent; 0 = never sent. */
    var lastAlertAt: Long
        get() = prefs.getLong(KEY_LAST_ALERT_AT, DEFAULT_LAST_ALERT_AT)
        set(value) = prefs.edit().putLong(KEY_LAST_ALERT_AT, value).apply()

    // --- Alert location prefs (Module 7) ---

    /** Whether a glucose alert is allowed to carry a location hint at all. Defaults to ON:
     * this is an emergency feature, and a feature that needs prior setup tends to be off
     * exactly when it would matter most, so the safer default is enabled-unless-turned-off. */
    var alertIncludeLocation: Boolean
        get() = prefs.getBoolean(KEY_ALERT_INCLUDE_LOCATION, DEFAULT_ALERT_INCLUDE_LOCATION)
        set(value) = prefs.edit().putBoolean(KEY_ALERT_INCLUDE_LOCATION, value).apply()

    /** Best-effort reverse-geocoded address for the last alert location saved via
     * [saveLastAlertLocation]. Null before the first save, or when reverse geocoding
     * did not resolve one. */
    var lastAlertLocationLabel: String?
        get() = prefs.getString(KEY_LAST_ALERT_LOCATION_LABEL, null)
        set(value) = prefs.edit().putString(KEY_LAST_ALERT_LOCATION_LABEL, value).apply()

    /** Latitude of the last alert location saved via [saveLastAlertLocation]. `SharedPreferences`
     * has no native double storage, so this is kept as a `Float` underneath — plenty of
     * precision for a map screen, never meant to drive clinical logic. */
    var lastAlertLocationLat: Double
        get() = prefs.getFloat(KEY_LAST_ALERT_LOCATION_LAT, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_LAST_ALERT_LOCATION_LAT, value.toFloat()).apply()

    /** Longitude counterpart to [lastAlertLocationLat]. */
    var lastAlertLocationLng: Double
        get() = prefs.getFloat(KEY_LAST_ALERT_LOCATION_LNG, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_LAST_ALERT_LOCATION_LNG, value.toFloat()).apply()

    /** Epoch millis of the last alert location saved; 0 = never saved. Same never-happened
     * sentinel as [lastAlertAt] and [lastReportAt]. */
    var lastAlertLocationAt: Long
        get() = prefs.getLong(KEY_LAST_ALERT_LOCATION_AT, DEFAULT_LAST_ALERT_LOCATION_AT)
        set(value) = prefs.edit().putLong(KEY_LAST_ALERT_LOCATION_AT, value).apply()

    /** Writes the last alert location in a single transaction — lat, lng, label and
     * timestamp all change together, the same reasoning as [saveTargetRange]. A single
     * record, not a history: only ever overwritten, never appended to a table. */
    fun saveLastAlertLocation(lat: Double, lng: Double, label: String?, atMillis: Long) {
        prefs.edit()
            .putFloat(KEY_LAST_ALERT_LOCATION_LAT, lat.toFloat())
            .putFloat(KEY_LAST_ALERT_LOCATION_LNG, lng.toFloat())
            .putString(KEY_LAST_ALERT_LOCATION_LABEL, label)
            .putLong(KEY_LAST_ALERT_LOCATION_AT, atMillis)
            .apply()
    }

    /** Writes the target range in a single transaction — both ends change together. */
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
        const val KEY_ALERT_MODE_HYPO = "alert_mode_hypo"
        const val KEY_ALERT_MODE_HYPER = "alert_mode_hyper"
        const val KEY_ALERT_THROTTLE_MIN = "alert_throttle_min"
        const val KEY_ALERT_ON_RECOVERY = "alert_on_recovery"
        const val KEY_LAST_ALERT_AT = "last_alert_at"
        const val KEY_ALERT_INCLUDE_LOCATION = "alert_include_location"
        const val KEY_LAST_ALERT_LOCATION_LABEL = "last_alert_location_label"
        const val KEY_LAST_ALERT_LOCATION_LAT = "last_alert_location_lat"
        const val KEY_LAST_ALERT_LOCATION_LNG = "last_alert_location_lng"
        const val KEY_LAST_ALERT_LOCATION_AT = "last_alert_location_at"

        const val DEFAULT_CHILD_NAME = "Lucas"
        const val DEFAULT_RANGE_MIN = 70
        const val DEFAULT_RANGE_MAX = 180
        const val DEFAULT_TARGET_GLUCOSE = 100
        const val DEFAULT_ISF = 50
        const val DEFAULT_IC_RATIO = 15
        const val DEFAULT_MAX_DOSE = 6

        /** Hypo defaults to AUTO (risk of loss of consciousness); hyper defaults to SUGGEST. */
        val DEFAULT_ALERT_MODE_HYPO = AlertMode.AUTO
        val DEFAULT_ALERT_MODE_HYPER = AlertMode.SUGGEST
        const val DEFAULT_ALERT_THROTTLE_MIN = 30
        const val DEFAULT_ALERT_ON_RECOVERY = true
        const val DEFAULT_LAST_ALERT_AT = 0L

        /** Emergency feature: defaults to on, never to off-until-configured. */
        const val DEFAULT_ALERT_INCLUDE_LOCATION = true
        const val DEFAULT_LAST_ALERT_LOCATION_AT = 0L

        /**
         * Absolute bounds allowed when editing the target range: wide enough to
         * cover any medically prescribed range, tight enough to reject a value
         * that could not plausibly be a blood glucose target.
         */
        const val RANGE_ABSOLUTE_MIN = 40
        const val RANGE_ABSOLUTE_MAX = 300

        /** Accepted bounds when editing the insulin sensitivity factor. */
        const val ISF_ABSOLUTE_MIN = 1
        const val ISF_ABSOLUTE_MAX = 500

        /** Accepted bounds when editing the insulin-to-carbohydrate ratio. */
        const val IC_RATIO_ABSOLUTE_MIN = 1
        const val IC_RATIO_ABSOLUTE_MAX = 100

        /** Accepted bounds when editing the single target glucose value. */
        const val TARGET_GLUCOSE_ABSOLUTE_MIN = 70
        const val TARGET_GLUCOSE_ABSOLUTE_MAX = 150

        /** Accepted bounds when editing the maximum dose lock. */
        const val MAX_DOSE_ABSOLUTE_MIN = 1
        const val MAX_DOSE_ABSOLUTE_MAX = 50
    }
}
