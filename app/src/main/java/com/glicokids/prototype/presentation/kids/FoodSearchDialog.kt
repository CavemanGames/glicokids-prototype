package com.glicokids.prototype.presentation.kids

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import com.glicokids.prototype.databinding.DialogFoodSearchBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * b9 · the "buscar alimento" dialog — one entry point for both the local `foods` table (filtered
 * in memory as the child types, see [NewMealViewModel.searchLocalFoods]) and an explicit online
 * search (see [NewMealViewModel.searchOnlineFoods]). Modeled after
 * [com.glicokids.prototype.presentation.parents.ContactDialog]: a plain object with one `show`,
 * a `MaterialAlertDialogBuilder` wrapping its own layout.
 *
 * It differs from `ContactDialog` in one way `ContactDialog` never needed: the results it shows
 * arrive asynchronously (a DB read, a network call), so it attaches its own [Observer] to
 * [NewMealViewModel.uiState] with `observeForever` — tying it to the activity's `LiveData`
 * observer would replay every unrelated emission (e.g. after `calculate()`) into a dialog that
 * might already be closed — and detaches it the moment the dialog is dismissed, by any means
 * (a pick, Cancelar, or the system back gesture).
 */
object FoodSearchDialog {

    fun show(
        activity: AppCompatActivity,
        viewModel: NewMealViewModel,
        onFoodSelected: (CarbsSelection) -> Unit
    ) {
        val binding = DialogFoodSearchBinding.inflate(activity.layoutInflater)

        val localAdapter = FoodLocalAdapter(activity)
        val onlineAdapter = FoodOnlineAdapter(activity)
        binding.lvLocalFoods.adapter = localAdapter
        binding.lvOnlineFoods.adapter = onlineAdapter

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Buscar alimento")
            .setView(binding.root)
            .setNegativeButton("Cancelar", null)
            .create()

        val stateObserver = Observer<NewMealUiState> { state ->
            localAdapter.submit(state.localFoodResults)
            binding.tvNoLocalResults.visibility =
                if (state.localFoodResults.isEmpty()) View.VISIBLE else View.GONE

            onlineAdapter.submit(state.onlineFoodResults)

            binding.rowSearchingOnline.visibility = if (state.isSearchingOnline) View.VISIBLE else View.GONE
            binding.btnSearchOnline.isEnabled = !state.isSearchingOnline
            binding.btnSearchOnline.alpha = if (state.isSearchingOnline) 0.5f else 1f

            binding.tvOnlineSearchError.text = state.onlineSearchError
            binding.tvOnlineSearchError.visibility =
                if (state.onlineSearchError.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        viewModel.uiState.observeForever(stateObserver)
        dialog.setOnDismissListener { viewModel.uiState.removeObserver(stateObserver) }

        binding.etFoodSearch.doAfterTextChanged { text ->
            viewModel.searchLocalFoods(text?.toString().orEmpty())
        }
        binding.btnSearchOnline.setOnClickListener {
            viewModel.searchOnlineFoods(binding.etFoodSearch.text?.toString().orEmpty())
        }

        binding.lvLocalFoods.setOnItemClickListener { _, _, position, _ ->
            val food = localAdapter.getItem(position)
            onFoodSelected(viewModel.applyLocalFood(food))
            dialog.dismiss()
        }
        binding.lvOnlineFoods.setOnItemClickListener { _, _, position, _ ->
            val product = onlineAdapter.getItem(position)
            onFoodSelected(viewModel.applyOnlineFood(product))
            dialog.dismiss()
        }

        dialog.show()
        // Loads the local table only once the observer above is already attached, so its
        // result — arriving on the next main-thread loop, not synchronously — is never missed.
        viewModel.openFoodSearch()
    }
}
