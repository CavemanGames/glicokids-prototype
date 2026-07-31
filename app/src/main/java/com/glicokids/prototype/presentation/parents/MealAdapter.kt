package com.glicokids.prototype.presentation.parents

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.glicokids.prototype.data.model.MealEntry
import com.glicokids.prototype.databinding.ItemMealBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** b17 · "Últimas refeições" list — data comes from the SQLite `meals` table. */
class MealAdapter : RecyclerView.Adapter<MealAdapter.MealViewHolder>() {

    private val items = mutableListOf<MealEntry>()

    fun submit(meals: List<MealEntry>) {
        items.clear()
        items.addAll(meals)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val binding = ItemMealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MealViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class MealViewHolder(private val binding: ItemMealBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(meal: MealEntry) {
            binding.tvMealWhen.text = "${whenLabel(meal.createdAt)} · ${meal.label}"
            binding.tvMealValues.text = String.format(
                Locale("pt", "BR"), "%.0fg · %.1f UI", meal.carbsG, meal.bolusUi
            )
        }

        private fun whenLabel(millis: Long): String {
            val time = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(millis))
            val day = SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(Date(millis))
            return if (isToday(millis)) "Hoje $time" else "$day $time"
        }

        private fun isToday(millis: Long): Boolean {
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = millis }
            return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        }
    }
}
