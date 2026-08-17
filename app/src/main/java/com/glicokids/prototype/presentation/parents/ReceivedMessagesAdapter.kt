package com.glicokids.prototype.presentation.parents

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.data.model.ReceivedMessage
import com.glicokids.prototype.databinding.ItemReceivedMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * b23 · one row per received message. A sender that matches no [Contact] shows
 * [ItemReceivedMessageBinding.tvAddUnknown] — the shortcut into the same add-contact
 * dialog [SupportNetworkActivity] uses (see [ContactDialog]), pre-filled with the phone.
 */
class ReceivedMessagesAdapter(
    private val context: Context,
    private val onAddUnknownClick: (ReceivedMessage) -> Unit
) : BaseAdapter() {

    private val messages = mutableListOf<ReceivedMessage>()
    private var contactsById: Map<Long, Contact> = emptyMap()

    fun submit(newMessages: List<ReceivedMessage>, newContactsById: Map<Long, Contact>) {
        messages.clear()
        messages.addAll(newMessages)
        contactsById = newContactsById
        notifyDataSetChanged()
    }

    override fun getCount(): Int = messages.size

    override fun getItem(position: Int): Any = messages[position]

    override fun getItemId(position: Int): Long = messages[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = convertView?.tag as? ItemReceivedMessageBinding
            ?: ItemReceivedMessageBinding.inflate(LayoutInflater.from(context), parent, false)
                .also { it.root.tag = it }
        val message = messages[position]
        val contact = message.contactId?.let { contactsById[it] }

        binding.tvMsgInitials.text = InitialsBadge.initialsFor(contact?.name ?: message.senderPhone)
        binding.tvMsgInitials.backgroundTintList =
            ColorStateList.valueOf(InitialsBadge.colorForPosition(context, position))
        binding.tvMsgInitials.contentDescription =
            "Iniciais de ${contact?.name ?: message.senderPhone}"

        binding.tvMsgSender.text = if (contact != null) {
            "${contact.name} · ${contact.relationship}"
        } else {
            message.senderPhone
        }
        binding.tvMsgWhen.text = TIME_FORMAT.format(Date(message.receivedAt))
        binding.tvMsgBody.text = message.body

        binding.tvAddUnknown.visibility = if (contact == null) View.VISIBLE else View.GONE
        binding.tvAddUnknown.setOnClickListener { onAddUnknownClick(message) }

        return binding.root
    }

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    }
}
