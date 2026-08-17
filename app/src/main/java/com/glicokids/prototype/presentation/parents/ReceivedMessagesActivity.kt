package com.glicokids.prototype.presentation.parents

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.glicokids.prototype.data.model.ReceivedMessage
import com.glicokids.prototype.databinding.ActivityReceivedMessagesBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * b23 — the incoming-message inbox [com.glicokids.prototype.data.sms.SmsReceiver] feeds.
 * A sender that resolves to no known contact shows the "número fora da rede" shortcut,
 * which opens the very same add-contact dialog b20 uses (see [ContactDialog]),
 * pre-filled with that phone number — reusing [SupportNetworkViewModel] instead of a
 * second contact-persistence path.
 */
@AndroidEntryPoint
class ReceivedMessagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceivedMessagesBinding
    private lateinit var adapter: ReceivedMessagesAdapter
    private val viewModel: ReceivedMessagesViewModel by viewModels()
    private val supportNetworkViewModel: SupportNetworkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceivedMessagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ReceivedMessagesAdapter(this) { message -> showAddUnknownDialog(message) }
        binding.lvMessages.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            adapter.submit(messages, viewModel.contactsById.value.orEmpty())
            binding.tvEmptyMessages.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
            binding.lvMessages.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.contactsById.observe(this) { contactsById ->
            adapter.submit(viewModel.messages.value.orEmpty(), contactsById)
        }
    }

    private fun showAddUnknownDialog(message: ReceivedMessage) {
        ContactDialog.show(
            activity = this,
            viewModel = supportNetworkViewModel,
            existing = null,
            initialPhone = message.senderPhone,
            onSaved = { viewModel.refresh() }
        )
    }
}
