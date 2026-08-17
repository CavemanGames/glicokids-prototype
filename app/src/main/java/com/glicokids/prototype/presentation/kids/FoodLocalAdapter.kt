package com.glicokids.prototype.presentation.kids

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.glicokids.prototype.data.model.Food
import com.glicokids.prototype.databinding.ItemFoodResultBinding

/**
 * b9 · rows of [Food] from the local `foods` table in the food search dialog — same
 * BaseAdapter+ViewBinding recycling idiom as [com.glicokids.prototype.presentation.parents.ContactAdapter].
 */
class FoodLocalAdapter(private val context: Context) : BaseAdapter() {

    private val items = mutableListOf<Food>()

    fun submit(newItems: List<Food>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Food = items[position]

    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = convertView?.tag as? ItemFoodResultBinding
            ?: ItemFoodResultBinding.inflate(LayoutInflater.from(context), parent, false)
                .also { it.root.tag = it }
        val food = items[position]

        binding.tvFoodResultName.text = food.nome
        binding.tvFoodResultDetail.text = "${food.porcao} · ${food.carboidratoG} g de carboidrato"

        return binding.root
    }
}
