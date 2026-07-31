package com.glicokids.prototype.presentation.parents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.glicokids.prototype.R
import com.glicokids.prototype.databinding.DialogTargetRangeBinding
import com.glicokids.prototype.databinding.FragmentParentAreaBinding
import com.glicokids.prototype.util.UIHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ParentAreaFragment : Fragment() {

    private var _binding: FragmentParentAreaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ParentAreaViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParentAreaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { requireActivity().finish() }
        
        binding.rowFsi.setOnClickListener { showParamDialog("FSI", "Fator de Sensibilidade", "fsi", 1, 500, "1:50") }
        binding.rowRatio.setOnClickListener { showParamDialog("I/C", "Relação Insulina/Carbo", "ratio", 1, 100, "1:15") }
        binding.rowTarget.setOnClickListener { showParamDialog("Alvo", "Alvo Glicêmico", "target", 70, 150, "100 mg/dL") }
        binding.rowRange.setOnClickListener { showRangeDialog() }
        binding.rowMaxDose.setOnClickListener { showParamDialog("Trava", "Dose Máxima", "max_dose", 1, 50, "6 UI") }
        // b18 · Não existe mais botão "Editar parâmetros": a edição é sempre por toque na linha.
    }

    private fun observeViewModel() {
        viewModel.targetRange.observe(viewLifecycleOwner) { range ->
            binding.rowRangeValue.text = "${range.first}–${range.second} ›"
        }
        
        // Mock values for now or read from storage
        binding.rowFsiValue.text = "1:50 ›"
        binding.rowRatioValue.text = "1:15 ›"
        binding.rowTargetValue.text = "100 mg/dL ›"
        binding.rowMaxDoseValue.text = "6 UI ›"
        
        setupMockChart()
    }

    private fun setupMockChart() {
        binding.llWeekChart.removeAllViews()
        val heights = listOf(40, 55, 30, 70, 45, 60, 50)
        heights.forEach { h ->
            val bar = View(requireContext()).apply {
                val params = LinearLayout.LayoutParams(0, (h * resources.displayMetrics.density).toInt(), 1f)
                params.setMargins(4, 0, 4, 0)
                layoutParams = params
                background = AppCompatResources.getDrawable(requireContext(), R.drawable.bg_chip_teal_soft)
            }
            binding.llWeekChart.addView(bar)
        }
    }

    private fun showParamDialog(title: String, label: String, key: String, min: Int, max: Int, current: String) {
        // Simple generic dialog for other params (Module 2 reuse)
        val et = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(current.filter { it.isDigit() })
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar $title")
            .setMessage("Ajuste o valor de $label")
            .setView(et)
            .setPositiveButton("Salvar") { _, _ -> 
                UIHelper.showToast(requireContext(), "Valor salvo: ${et.text}")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showRangeDialog() {
        val currentRange = viewModel.targetRange.value ?: Pair(70, 180)
        val dialogBinding = DialogTargetRangeBinding.inflate(layoutInflater)
        
        dialogBinding.etRangeMin.setText(currentRange.first.toString())
        dialogBinding.etRangeMax.setText(currentRange.second.toString())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar Faixa Alvo")
            .setView(dialogBinding.root)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val minStr = dialogBinding.etRangeMin.text.toString()
            val maxStr = dialogBinding.etRangeMax.text.toString()
            val min = minStr.toIntOrNull() ?: 0
            val max = maxStr.toIntOrNull() ?: 0

            if (min < 40 || min >= max || max > 300) {
                if (min < 40) dialogBinding.tilRangeMin.error = "Mínimo 40"
                else if (min >= max) dialogBinding.tilRangeMin.error = "Mínimo deve ser menor que máximo"
                
                if (max > 300) dialogBinding.tilRangeMax.error = "Máximo 300"
            } else {
                if (viewModel.updateTargetRange(min, max)) {
                    dialog.dismiss()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
