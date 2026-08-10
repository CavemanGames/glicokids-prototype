package com.glicokids.prototype.presentation.kids

import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.glicokids.prototype.R
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.databinding.ActivityGalleryBinding
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private var medals: List<Medal> = emptyList()

    @Inject lateinit var dbHelper: GlicoKidsDbHelper
    @Inject lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { shareStreak() }

        binding.tvStreakCount.text = prefs.streak.toString()
        binding.tvStreakLabel.text =
            "dias seguidos com a glicose na meta · ${prefs.childName} está mandando bem"

        loadMedals()
    }

    /** Module 5 — requirement 7: medals come from the `medals` table, not a fixed list. */
    private fun loadMedals() {
        lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) { dbHelper.getMedals() }
            medals = records.map { record ->
                Medal(
                    id = record.id.toInt(),
                    name = record.name,
                    description = describe(record.name, record.unlocked),
                    rarity = record.rarity,
                    drawableRes = drawableForRarity(record.rarity),
                    isLocked = !record.unlocked
                )
            }
            binding.gvMedals.adapter = MedalAdapter(this@GalleryActivity, medals) { anchor, medal ->
                showMedalOptions(anchor, medal)
            }
            // Long-press keeps working (Module 4 requirement) — both paths call the same
            // handleMedalAction, only the entry point (ContextMenu vs PopupMenu) differs.
            registerForContextMenu(binding.gvMedals)
        }
    }

    private fun drawableForRarity(rarity: String): Int = when (rarity) {
        "OURO" -> R.drawable.ic_medal_gold
        "ESMERALDA" -> R.drawable.ic_medal_teal
        else -> R.drawable.ic_medal_purple
    }

    private fun describe(name: String, unlocked: Boolean): String =
        if (unlocked) "Conquistada: $name" else "Bloqueada: continue nas missões para liberar."

    override fun onCreateContextMenu(
        menu: ContextMenu?,
        v: View?,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val info = menuInfo as? AdapterView.AdapterContextMenuInfo ?: return
        val medal = medals.getOrNull(info.position) ?: return

        menu?.setHeaderTitle("${medal.name} · ${medal.rarity}")
        menu?.add(0, 1, 0, "Ver Detalhes")
        menu?.add(0, 2, 1, "Compartilhar")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val info = item.menuInfo as? AdapterView.AdapterContextMenuInfo
            ?: return super.onContextItemSelected(item)
        val medal = medals.getOrNull(info.position) ?: return super.onContextItemSelected(item)

        return handleMedalAction(item.itemId, medal) || super.onContextItemSelected(item)
    }

    /**
     * Module 6 · discoverability: the "⋮" button on unlocked medals (item_medal.xml) opens
     * the same two actions as the long-press context menu, with the same header.
     * Same construction as [KidsDashboardFragment]'s own "⋮" popup: without the
     * ContextThemeWrapper the popup renders as a plain white rectangular block, breaking
     * visual fidelity (popupMenuStyle/actionOverflowMenuStyle only cover the theme's own
     * default PopupMenu, not one built with a bare Activity context).
     */
    private fun showMedalOptions(anchor: View, medal: Medal) {
        val popupContext = ContextThemeWrapper(this, R.style.ThemeOverlay_GlicoKids_Popup)
        PopupMenu(popupContext, anchor).apply {
            menu.add(0, 0, 0, "${medal.name} · ${medal.rarity}").isEnabled = false
            menu.add(0, 1, 1, "Ver Detalhes")
            menu.add(0, 2, 2, "Compartilhar")
            setOnMenuItemClickListener { item -> handleMedalAction(item.itemId, medal) }
        }.show()
    }

    /** Single source of truth for both entry points (long-press context menu and "⋮"). */
    private fun handleMedalAction(itemId: Int, medal: Medal): Boolean {
        return when (itemId) {
            1 -> {
                UIHelper.showToast(this, medal.description)
                true
            }
            2 -> {
                shareMedal(medal)
                true
            }
            else -> false
        }
    }

    private fun shareMedal(medal: Medal) {
        share("Ganhei a medalha ${medal.name} no GlicoKids!")
    }

    private fun shareStreak() {
        share("Estou há ${prefs.streak} dias na meta no GlicoKids!")
    }

    private fun share(text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, null))
    }
}
