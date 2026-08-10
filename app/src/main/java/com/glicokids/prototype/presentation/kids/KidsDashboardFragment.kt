package com.glicokids.prototype.presentation.kids

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.glicokids.prototype.R
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.databinding.FragmentKidsDashboardBinding
import com.glicokids.prototype.domain.usecase.DashboardGlucoseDisplay
import com.glicokids.prototype.domain.usecase.GetDashboardGlucoseDisplayUseCase
import com.glicokids.prototype.domain.usecase.GetGlucoseTrendUseCase
import com.glicokids.prototype.domain.usecase.GetMedalsCountLabelUseCase
import com.glicokids.prototype.domain.usecase.GlucoseTrendDisplay
import com.glicokids.prototype.presentation.parents.ParentSecurityActivity
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class KidsDashboardFragment : Fragment() {

    private var _binding: FragmentKidsDashboardBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var prefs: com.glicokids.prototype.data.local.AppPreferences

    @Inject
    lateinit var dbHelper: GlicoKidsDbHelper

    @Inject
    lateinit var getDashboardGlucoseDisplayUseCase: GetDashboardGlucoseDisplayUseCase

    @Inject
    lateinit var getGlucoseTrendUseCase: GetGlucoseTrendUseCase

    @Inject
    lateinit var getMedalsCountLabelUseCase: GetMedalsCountLabelUseCase

    private val avatars = intArrayOf(
        R.drawable.ic_avatar_1, R.drawable.ic_avatar_2, R.drawable.ic_avatar_3,
        R.drawable.ic_avatar_4, R.drawable.ic_avatar_5
    )

    /** A correct PIN (b10) unlocks the Parent Area (b17) through nav_graph. */
    private val parentAreaLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                findNavController().navigate(R.id.action_kidsDashboard_to_parentArea)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKidsDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        setupAnimations()

        binding.btnParentArea.setOnClickListener {
            // Module 2 requirement: navigation via Intent carrying Extras
            val intent = Intent(requireContext(), ParentSecurityActivity::class.java).apply {
                putExtra("CHILD_NAME", prefs.childName)
            }
            parentAreaLauncher.launch(intent)
        }

        binding.btnNewMeal.setOnClickListener {
            // Module 3: opening the Meal Mission via Intent
            val intent = Intent(requireContext(), NewMealActivity::class.java).apply {
                putExtra("CHILD_NAME", prefs.childName)
            }
            startActivity(intent)
        }

        binding.btnMedals.setOnClickListener {
            UIHelper.navigateTo(requireContext(), GalleryActivity::class.java)
        }

        binding.cardGlucose.setOnClickListener {
            UIHelper.navigateTo(requireContext(), GlucoseLogActivity::class.java)
        }
    }

    // b8 · The rounded popup comes from the THEME (popupMenuStyle + ThemeOverlay.GlicoKids.Popup),
    // never from a custom layout. Iconless single-line items, declared in res/menu/main_menu.xml.
    private fun setupMenu() {
        val popupContext = ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_GlicoKids_Popup)

        binding.btnMenu.setOnClickListener {
            PopupMenu(popupContext, binding.btnMenu).apply {
                menuInflater.inflate(R.menu.main_menu, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_gallery -> UIHelper.navigateTo(requireContext(), GalleryActivity::class.java)
                        R.id.menu_avatar -> UIHelper.navigateTo(requireContext(), AvatarActivity::class.java)
                        R.id.menu_help -> UIHelper.navigateTo(requireContext(), HelpActivity::class.java)
                        else -> return@setOnMenuItemClickListener false
                    }
                    true
                }
            }.show()
        }
    }

    private fun setupAnimations() {
        // Pulse effect for the main button
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f)
        
        ObjectAnimator.ofPropertyValuesHolder(binding.btnNewMeal, scaleX, scaleY).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Orbit rotation effect
        ObjectAnimator.ofFloat(binding.vOrbit, View.ROTATION, 0f, 360f).apply {
            duration = 10000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }
    }

    /**
     * Module 5 — requirement 2: the home reads what other Activities wrote into the
     * same SharedPreferences (chosen avatar, XP, coins, target range).
     */
    override fun onResume() {
        super.onResume()
        binding.ivAvatar.setImageResource(avatars[prefs.avatarIndex.coerceIn(avatars.indices)])
        binding.tvWelcome.text = "Oi, ${prefs.childName}!"
        binding.tvCoins.text = prefs.coins.toString()
        binding.pbXp.progress = prefs.xp % 100
        binding.tvLevel.text = "Nv ${prefs.xp / 100 + 1}"
        updateGlucoseDisplay()
        updateMedalsCount()
    }

    /**
     * Module 6 — field defect: this card used to show a hardcoded "112 mg/dL · Na meta!" no
     * matter what the child had logged. The last reading now comes from
     * [GlicoKidsDbHelper.getLastGlucoseReading] off the main thread, and the whole decision of
     * what to show is [GetDashboardGlucoseDisplayUseCase]'s alone — never recompute the status
     * here. While the query is still running (first [onResume] of the process) the card shows
     * the same empty state as "no reading yet" instead of any number, so there is never a
     * moment with a stale or made-up value on screen.
     */
    private fun updateGlucoseDisplay() {
        renderGlucoseDisplay(
            getDashboardGlucoseDisplayUseCase.execute(lastReading = null, rangeMin = prefs.rangeMin, rangeMax = prefs.rangeMax)
        )
        renderTrend(GlucoseTrendDisplay(label = "", hasEnoughData = false))

        viewLifecycleOwner.lifecycleScope.launch {
            val (display, trend) = withContext(Dispatchers.IO) {
                // Most recent first: index 0 is the current reading, index 1 the previous one.
                val recentReadings = dbHelper.getRecentGlucoseReadings(2)
                val lastReading = recentReadings.getOrNull(0)
                val previousReading = recentReadings.getOrNull(1)
                val display = getDashboardGlucoseDisplayUseCase.execute(lastReading, prefs.rangeMin, prefs.rangeMax)
                val trend = getGlucoseTrendUseCase.execute(previousReading, lastReading)
                display to trend
            }
            if (_binding != null) {
                renderGlucoseDisplay(display)
                renderTrend(trend)
            }
        }
    }

    private fun renderGlucoseDisplay(display: DashboardGlucoseDisplay) {
        val color = display.status?.let { UIHelper.getStatusColor(it) }
            ?: android.graphics.Color.parseColor("#8A8299") // neutral grey — no clinical status to show yet

        binding.tvGlucoseValue.text = display.valueMgdl?.toString() ?: "--"
        binding.tvGlucoseValue.setTextColor(display.valueColorArgb)
        binding.cardGlucose.strokeColor = color
        binding.tvGlucoseStatus.text = "mg/dL · ${display.statusLabel}"
        binding.tvGlucoseCaption.text = display.secondaryMessage

        // Update trend chip background based on status
        binding.tvTrend.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        binding.tvTrend.setTextColor(if (display.status == UIHelper.GlucoseStatus.NA_META)
            android.graphics.Color.parseColor("#221650") else android.graphics.Color.WHITE)
    }

    /**
     * Module 6 — field defect: the trend chip was hardcoded to "→ estável" in the layout and
     * never written to, so a brand-new install still claimed a stable trend with zero readings.
     * With fewer than two readings there is nothing to compare, so the chip is hidden entirely
     * instead of showing a label the app cannot honestly back up.
     */
    private fun renderTrend(trend: GlucoseTrendDisplay) {
        binding.tvTrend.visibility = if (trend.hasEnoughData) View.VISIBLE else View.GONE
        if (trend.hasEnoughData) {
            binding.tvTrend.text = trend.label
        }
    }

    /**
     * Module 6 — field defect: the "★ Medalhas" button was hardcoded to "12 conquistadas" no
     * matter how many medals the child had actually unlocked (the seed data starts at 4/6).
     */
    private fun updateMedalsCount() {
        viewLifecycleOwner.lifecycleScope.launch {
            val label = withContext(Dispatchers.IO) {
                getMedalsCountLabelUseCase.execute(dbHelper.getMedals())
            }
            if (_binding != null) {
                binding.btnMedals.text = "★ Medalhas\n$label"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
