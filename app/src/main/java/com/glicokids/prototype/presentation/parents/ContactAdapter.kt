package com.glicokids.prototype.presentation.parents

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.core.content.ContextCompat
import com.glicokids.prototype.R
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.databinding.ItemContactBinding

/**
 * b19 · one row per support-network member. [onMoreOptionsClick] is the visible "⋮"
 * entry point into the same context menu the list's long-press also opens — see
 * [SupportNetworkActivity.showContactOptions] / [SupportNetworkActivity.onCreateContextMenu],
 * both of which resolve to [SupportNetworkActivity.handleContactAction]. Same discoverability
 * pattern already used for medals ([com.glicokids.prototype.presentation.kids.MedalAdapter]).
 */
class ContactAdapter(
    private val context: Context,
    private val onMoreOptionsClick: (View, Contact) -> Unit,
    private val onAlertToggle: (Contact, Boolean) -> Unit,
    private val onReportToggle: (Contact, Boolean) -> Unit
) : BaseAdapter() {

    private val contacts = mutableListOf<Contact>()

    fun submit(newContacts: List<Contact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = contacts.size

    override fun getItem(position: Int): Any = contacts[position]

    override fun getItemId(position: Int): Long = contacts[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = convertView?.tag as? ItemContactBinding
            ?: ItemContactBinding.inflate(LayoutInflater.from(context), parent, false)
                .also { it.root.tag = it }
        val contact = contacts[position]

        binding.tvContactInitials.text = initialsFor(contact.name)
        binding.tvContactInitials.backgroundTintList = ColorStateList.valueOf(colorForPosition(position))
        binding.tvContactInitials.contentDescription = "Iniciais de ${contact.name}"

        binding.tvContactName.text = contact.name
        binding.tvContactRelationship.text = contact.relationship
        binding.tvContactPrimary.visibility = if (contact.isPrimary) View.VISIBLE else View.GONE
        binding.tvContactChannels.text = channelsLabel(contact)

        binding.btnContactMenu.contentDescription = "Mais opções de ${contact.name}"
        binding.btnContactMenu.setOnClickListener { anchor -> onMoreOptionsClick(anchor, contact) }

        // Listener detached before the programmatic isChecked assignment so a recycled
        // row does not fire a spurious toggle for the contact it is about to display.
        binding.swReceivesAlert.setOnCheckedChangeListener(null)
        binding.swReceivesAlert.isChecked = contact.receivesAlert
        binding.swReceivesAlert.contentDescription =
            "SMS de alerta para ${contact.name}, ${stateLabel(contact.receivesAlert)}"
        binding.swReceivesAlert.setOnCheckedChangeListener { _, checked -> onAlertToggle(contact, checked) }

        binding.swReceivesReport.setOnCheckedChangeListener(null)
        binding.swReceivesReport.isChecked = contact.receivesReport
        binding.swReceivesReport.contentDescription =
            "Relatório para ${contact.name}, ${stateLabel(contact.receivesReport)}"
        binding.swReceivesReport.setOnCheckedChangeListener { _, checked -> onReportToggle(contact, checked) }

        return binding.root
    }

    private fun stateLabel(enabled: Boolean) = if (enabled) "ligado" else "desligado"

    private fun channelsLabel(contact: Contact): String =
        if (contact.email.isNullOrBlank()) contact.phone else "${contact.phone} · ${contact.email}"

    private fun initialsFor(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts.first().take(2).uppercase()
            else -> "${parts.first().first()}${parts.last().first()}".uppercase()
        }
    }

    private fun colorForPosition(position: Int): Int =
        ContextCompat.getColor(context, INITIALS_COLORS[position % INITIALS_COLORS.size])

    companion object {
        private val INITIALS_COLORS = listOf(
            R.color.primary, R.color.primary_dark, R.color.gold_dark, R.color.teal_dark
        )
    }
}
