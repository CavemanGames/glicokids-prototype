package com.glicokids.prototype.presentation.parents

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.glicokids.prototype.R
import com.glicokids.prototype.databinding.DialogTargetRangeBinding
import com.glicokids.prototype.databinding.FragmentParentAreaBinding
import com.glicokids.prototype.util.UIHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ParentAreaFragment : Fragment() {

    private var _binding: FragmentParentAreaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ParentAreaViewModel by viewModels()
    private val mealAdapter = MealAdapter()

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

        binding.rvMeals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMeals.adapter = mealAdapter

        setupListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from logging a reading or a meal, already with fresh SQLite data.
        viewModel.refresh()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { requireActivity().finish() }

        binding.rowFsi.setOnClickListener {
            showParamDialog(
                title = "Fator de Sensibilidade",
                help = "Quanto 1 UI de insulina baixa a glicemia, em mg/dL.",
                param = ParentAreaViewModel.Param.ISF,
                current = viewModel.params.value?.isf ?: 0
            )
        }
        binding.rowRatio.setOnClickListener {
            showParamDialog(
                title = "Relação Insulina/Carbo",
                help = "Quantos gramas de carboidrato são cobertos por 1 UI.",
                param = ParentAreaViewModel.Param.IC_RATIO,
                current = viewModel.params.value?.icRatio ?: 0
            )
        }
        binding.rowTarget.setOnClickListener {
            showParamDialog(
                title = "Alvo Glicêmico",
                help = "Valor de glicemia usado como alvo na correção.",
                param = ParentAreaViewModel.Param.TARGET,
                current = viewModel.params.value?.targetGlucose ?: 0
            )
        }
        binding.rowMaxDose.setOnClickListener {
            showParamDialog(
                title = "Trava de Dose Máxima",
                help = "Teto de segurança para a dose sugerida, em UI.",
                param = ParentAreaViewModel.Param.MAX_DOSE,
                current = viewModel.params.value?.maxDose ?: 0
            )
        }
        binding.rowRange.setOnClickListener { showRangeDialog() }

        binding.btnViewReport.setOnClickListener { showReport() }
        binding.cvReport.setOnClickListener { showReport() }
        binding.btnExportReport.setOnClickListener { exportReport() }
        binding.btnExportExternal.setOnClickListener { confirmExternalCopy() }
        binding.btnEmailReport.setOnClickListener { emailReport() }

        // b19 — protected by PIN simply by living inside the Parent Area.
        binding.cvSupportNetwork.setOnClickListener {
            UIHelper.navigateTo(requireContext(), SupportNetworkActivity::class.java)
        }

        // Module 7 (b-map) — same PIN protection, same navigation idiom.
        binding.cvMap.setOnClickListener {
            UIHelper.navigateTo(requireContext(), AlertMapActivity::class.java)
        }
    }

    private fun observeViewModel() {
        viewModel.params.observe(viewLifecycleOwner) { p ->
            binding.rowFsiValue.text = "1:${p.isf} ›"
            binding.rowRatioValue.text = "1:${p.icRatio} ›"
            binding.rowTargetValue.text = "${p.targetGlucose} mg/dL ›"
            binding.rowRangeValue.text = "${p.rangeMin}–${p.rangeMax} ›"
            binding.rowMaxDoseValue.text = "${p.maxDose} UI ›"
        }

        viewModel.weekBars.observe(viewLifecycleOwner) { renderWeekChart(it) }

        viewModel.timeInRange.observe(viewLifecycleOwner) {
            binding.tvTimeInRange.text = "$it% na meta"
        }

        viewModel.meals.observe(viewLifecycleOwner) { mealAdapter.submit(it) }

        viewModel.supportNetworkSummary.observe(viewLifecycleOwner) { text ->
            binding.tvSupportNetworkSummary.text = text
        }

        viewModel.lastReportAt.observe(viewLifecycleOwner) { at ->
            binding.tvLastReport.text = if (at <= 0L) {
                "Nenhum relatório gerado ainda"
            } else {
                val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
                "Último: ${stamp.format(Date(at))}"
            }
        }
    }

    // ------------------------------------------------------------------
    // 7-day chart — data from SQLite, colour always through glucoseStatus
    // ------------------------------------------------------------------

    private fun renderWeekChart(bars: List<ParentAreaViewModel.DayBar>) {
        binding.llWeekChart.removeAllViews()
        binding.llWeekLabels.removeAllViews()

        val density = resources.displayMetrics.density
        val maxHeightPx = (70 * density).toInt()
        val peak = bars.maxOfOrNull { it.average }?.coerceAtLeast(1) ?: 1

        bars.forEach { bar ->
            val heightPx = if (!bar.hasData) {
                (6 * density).toInt()
            } else {
                (maxHeightPx * bar.average / peak).coerceAtLeast((10 * density).toInt())
            }

            val view = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, heightPx, 1f).also {
                    it.setMargins((3 * density).toInt(), 0, (3 * density).toInt(), 0)
                }
                background = ResourcesCompat.getDrawable(resources, R.drawable.bg_chip_teal_soft, null)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (bar.hasData) UIHelper.getStatusColor(bar.status) else Color.parseColor("#E3DAF7")
                )
                alpha = if (bar.hasData) 1f else 0.5f
            }
            binding.llWeekChart.addView(view)

            val label = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = bar.label
                gravity = Gravity.CENTER
                textSize = 10f
                setTextColor(Color.parseColor("#8A80A8"))
            }
            binding.llWeekLabels.addView(label)
        }
    }

    // ------------------------------------------------------------------
    // Report
    // ------------------------------------------------------------------

    /** Requirement 3 — FileOutputStream. */
    private fun exportReport() {
        viewModel.exportReport { _ ->
            UIHelper.showToast(requireContext(), "Relatório gerado em relatorio_glicokids.txt")
        }
    }

    /** Requirement 4 — FileInputStream + InputStreamReader, shown in a scrollable dialog. */
    private fun showReport() {
        viewModel.readReport { content ->
            if (content == null) {
                UIHelper.showToast(requireContext(), "Gere o relatório primeiro")
                return@readReport
            }

            val padding = (20 * resources.displayMetrics.density).toInt()
            val text = TextView(requireContext()).apply {
                setText(content)
                setTextIsSelectable(true)
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(ResourcesCompat.getColor(resources, R.color.ink, null))
                setPadding(padding, padding, padding, padding)
            }
            val scroll = ScrollView(requireContext()).apply { addView(text) }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Relatório · últimos 7 dias")
                .setView(scroll)
                .setPositiveButton("Fechar", null)
                .show()
        }
    }

    /**
     * Requirement 4 — hands the same 7-day report off to an e-mail client via
     * [UIHelper.sendEmailWithBody]. Recipients come from [ParentAreaViewModel.getReportRecipients]
     * (contacts with `receivesReport` on and an e-mail); the content is the same
     * report [showReport] already reads through [reportStorage][ParentAreaViewModel.readReport].
     */
    private fun emailReport() {
        val recipients = viewModel.getReportRecipients()
        if (recipients.isEmpty()) {
            UIHelper.showToast(requireContext(), "Nenhum contato configurado para receber o relatório por e-mail")
            return
        }

        viewModel.readReport { content ->
            if (content == null) {
                UIHelper.showToast(requireContext(), "Gere o relatório primeiro")
                return@readReport
            }

            val sent = UIHelper.sendEmailWithBody(
                requireContext(),
                recipients.toTypedArray(),
                "Relatório GlicoKids · últimos 7 dias",
                content
            )
            if (!sent) UIHelper.showToast(requireContext(), "Nenhum aplicativo de e-mail encontrado no aparelho")
        }
    }

    /** Requirement 5 — health data leaving the sandbox requires confirmation (LGPD). */
    private fun confirmExternalCopy() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Salvar cópia fora do app?")
            .setMessage(
                "O relatório contém dados de saúde da criança. " +
                    "Salvar uma cópia fora do aplicativo?"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar cópia") { _, _ ->
                viewModel.exportReportExternally { path ->
                    if (path == null) {
                        UIHelper.showToast(requireContext(), "Não foi possível salvar a cópia externa")
                    } else {
                        UIHelper.showToast(requireContext(), "Cópia salva em $path")
                    }
                }
            }
            .show()
    }

    // ------------------------------------------------------------------
    // Parameter editing (b18) — always a validated dialog, never inline
    // ------------------------------------------------------------------

    private fun showParamDialog(
        title: String,
        help: String,
        param: ParentAreaViewModel.Param,
        current: Int
    ) {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val input = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            setSelection(text.length)
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
            addView(TextView(requireContext()).apply {
                text = "aceito: ${param.range.first} a ${param.range.last}"
                textSize = 11f
                setTextColor(Color.parseColor("#8A80A8"))
            })
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar $title")
            .setMessage(help)
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.text.toString().toIntOrNull()
            if (value == null || !viewModel.updateParam(param, value)) {
                input.error = "Informe um valor entre ${param.range.first} e ${param.range.last}"
            } else {
                dialog.dismiss()
            }
        }
    }

    private fun showRangeDialog() {
        val current = viewModel.targetRange.value ?: return
        val dialogBinding = DialogTargetRangeBinding.inflate(layoutInflater)

        dialogBinding.etRangeMin.setText(current.first.toString())
        dialogBinding.etRangeMax.setText(current.second.toString())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar Faixa Alvo")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialogBinding.tilRangeMin.error = null
            dialogBinding.tilRangeMax.error = null

            val min = dialogBinding.etRangeMin.text.toString().toIntOrNull() ?: 0
            val max = dialogBinding.etRangeMax.text.toString().toIntOrNull() ?: 0

            if (viewModel.updateTargetRange(min, max)) {
                dialog.dismiss()
                UIHelper.showToast(requireContext(), "Faixa alvo atualizada para $min–$max mg/dL")
            } else {
                if (min < 40) dialogBinding.tilRangeMin.error = "Mínimo 40"
                else if (min >= max) dialogBinding.tilRangeMin.error = "Mínimo deve ser menor que o máximo"
                if (max > 300) dialogBinding.tilRangeMax.error = "Máximo 300"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMeals.adapter = null
        _binding = null
    }
}
