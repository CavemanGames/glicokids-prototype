package com.glicokids.prototype.presentation.parents

import android.content.Context
import androidx.core.content.ContextCompat
import com.glicokids.prototype.R

/**
 * Shared by [ContactAdapter] and [ReceivedMessagesAdapter] — both render a colored circle
 * with a person's initials, and there is no reason for two copies of the same two rules.
 */
object InitialsBadge {

    private val COLORS = listOf(
        R.color.primary, R.color.primary_dark, R.color.gold_dark, R.color.teal_dark
    )

    fun initialsFor(source: String): String {
        val parts = source.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts.first().take(2).uppercase()
            else -> "${parts.first().first()}${parts.last().first()}".uppercase()
        }
    }

    fun colorForPosition(context: Context, position: Int): Int =
        ContextCompat.getColor(context, COLORS[position % COLORS.size])
}
