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
import androidx.navigation.fragment.findNavController
import com.glicokids.prototype.R
import com.glicokids.prototype.databinding.FragmentKidsDashboardBinding
import com.glicokids.prototype.presentation.parents.ParentSecurityActivity
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KidsDashboardFragment : Fragment() {

    private var _binding: FragmentKidsDashboardBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var storageRepository: com.glicokids.prototype.domain.repository.StorageRepository

    /** PIN correto (b10) libera a Área dos Pais (b17) pelo nav_graph. */
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
        updateGlucoseDisplay()

        binding.btnParentArea.setOnClickListener {
            // Requisito Módulo 2: Navegação via Intent com Passagem de Dados (Extras)
            val intent = Intent(requireContext(), ParentSecurityActivity::class.java).apply {
                putExtra("CHILD_NAME", "Lucas")
            }
            parentAreaLauncher.launch(intent)
        }

        binding.btnNewMeal.setOnClickListener {
            // Módulo 3: Abrindo Missão da Refeição via Intent
            val intent = Intent(requireContext(), NewMealActivity::class.java).apply {
                putExtra("CHILD_NAME", "Lucas")
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

    // b8 · O popup arredondado vem do TEMA (popupMenuStyle + ThemeOverlay.GlicoKids.Popup),
    // nunca de layout customizado. Itens sem ícone, em uma linha, definidos em res/menu/main_menu.xml.
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

    private fun updateGlucoseDisplay() {
        val currentGlucose = 112 // Mocked for Dashboard
        val min = storageRepository.getInt("range_min", 70)
        val max = storageRepository.getInt("range_max", 180)

        val status = UIHelper.glucoseStatus(currentGlucose, min, max)
        val color = UIHelper.getStatusColor(status)

        binding.cardGlucose.strokeColor = color
        binding.tvGlucoseStatus.text = when (status) {
            UIHelper.GlucoseStatus.NA_META -> "mg/dL · Na meta!"
            UIHelper.GlucoseStatus.ATENCAO -> "mg/dL · Atenção"
            UIHelper.GlucoseStatus.FORA_DA_META -> "mg/dL · Fora da meta"
        }
        
        // Update trend chip background based on status
        binding.tvTrend.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        binding.tvTrend.setTextColor(if (status == UIHelper.GlucoseStatus.NA_META) 
            android.graphics.Color.parseColor("#221650") else android.graphics.Color.WHITE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
