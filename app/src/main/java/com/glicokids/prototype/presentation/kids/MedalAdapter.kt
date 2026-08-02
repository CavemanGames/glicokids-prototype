package com.glicokids.prototype.presentation.kids

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.glicokids.prototype.R

/**
 * Module 6 · discoverability: [onMoreOptionsClick] opens the exact same options
 * (Ver Detalhes / Compartilhar) as the long-press context menu — the visible "⋮"
 * is just a second entry point into [GalleryActivity.showMedalOptions].
 */
class MedalAdapter(
    private val context: Context,
    private val medals: List<Medal>,
    private val onMoreOptionsClick: (View, Medal) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = medals.size

    override fun getItem(position: Int): Any = medals[position]

    override fun getItemId(position: Int): Long = medals[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_medal, parent, false)
        val medal = medals[position]

        val ivIcon = view.findViewById<ImageView>(R.id.ivMedalIcon)
        val tvName = view.findViewById<TextView>(R.id.tvMedalName)
        val btnMore = view.findViewById<ImageButton>(R.id.btnMedalMore)

        tvName.text = medal.name

        if (medal.isLocked) {
            view.alpha = 0.45f
            ivIcon.setImageResource(R.drawable.ic_medal_locked)
            ivIcon.clearColorFilter()
            ivIcon.contentDescription = "Medalha ${medal.name}, bloqueada"
            btnMore.visibility = View.GONE
            btnMore.setOnClickListener(null)
        } else {
            view.alpha = 1.0f
            ivIcon.setImageResource(medal.drawableRes)
            ivIcon.clearColorFilter()
            ivIcon.contentDescription = "Medalha ${medal.name}, ${rarityLabel(medal.rarity)}, conquistada"
            btnMore.visibility = View.VISIBLE
            btnMore.setOnClickListener { anchor -> onMoreOptionsClick(anchor, medal) }
        }

        return view
    }

    private fun rarityLabel(rarity: String): String = when (rarity) {
        "OURO" -> "ouro"
        "ESMERALDA" -> "esmeralda"
        "DIAMANTE" -> "diamante"
        else -> rarity.lowercase()
    }
}
