package com.glicokids.prototype.util

import android.content.Context
import android.content.Intent
import android.widget.Toast

object UIHelper {
    
    enum class GlucoseStatus {
        NA_META, ATENCAO, FORA_DA_META
    }

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun navigateTo(context: Context, destination: Class<*>) {
        val intent = Intent(context, destination)
        context.startActivity(intent)
    }

    fun glucoseStatus(value: Int, min: Int, max: Int): GlucoseStatus {
        return when {
            value < min || value > max -> GlucoseStatus.FORA_DA_META
            value in min..(min + 15) || value in (max - 15)..max -> GlucoseStatus.ATENCAO
            else -> GlucoseStatus.NA_META
        }
    }

    /** Mesma cor do estado, com opacidade reduzida — para fundo de chip/barra. */
    fun withAlpha(color: Int, alpha: Float): Int =
        android.graphics.Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color)
        )

    fun getStatusColor(status: GlucoseStatus): Int {
        return when (status) {
            GlucoseStatus.NA_META -> android.graphics.Color.parseColor("#00E5B0") // Teal
            GlucoseStatus.ATENCAO -> android.graphics.Color.parseColor("#FFD54F") // Gold
            GlucoseStatus.FORA_DA_META -> android.graphics.Color.parseColor("#C62828") // Danger
        }
    }
}
