package com.glicokids.prototype.presentation.kids

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.glicokids.prototype.databinding.ItemFoodResultBinding
import com.glicokids.prototype.domain.model.FoodProduct

/**
 * b9 · rows of [FoodProduct] from Open Food Facts in the food search dialog. Shares
 * [com.glicokids.prototype.R.layout.item_food_result] with [FoodLocalAdapter] — same shape,
 * different source and detail line (brand + per-100g carbs instead of a fixed local portion).
 */
class FoodOnlineAdapter(private val context: Context) : BaseAdapter() {

    private val items = mutableListOf<FoodProduct>()

    fun submit(newItems: List<FoodProduct>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): FoodProduct = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = convertView?.tag as? ItemFoodResultBinding
            ?: ItemFoodResultBinding.inflate(LayoutInflater.from(context), parent, false)
                .also { it.root.tag = it }
        val product = items[position]

        binding.tvFoodResultName.text = product.name
        binding.tvFoodResultDetail.text = buildString {
            if (!product.brand.isNullOrBlank()) append("${product.brand} · ")
            append(CarbsSelectionFormatter.carbsFieldText(product.carbsPer100g))
            append(" g de carboidrato por 100 g")
        }

        return binding.root
    }
}
