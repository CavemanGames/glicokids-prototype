package com.glicokids.prototype.presentation.parents

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.data.model.ReceivedMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * b23 — Received transmissions. [contactsById] backs the sender lookup the adapter needs
 * to render name/relationship/initials; [com.glicokids.prototype.data.sms.SmsReceiver]
 * already resolved the contact at receive time, but only the id is stored on the row.
 */
@HiltViewModel
class ReceivedMessagesViewModel @Inject constructor(
    private val dbHelper: GlicoKidsDbHelper
) : ViewModel() {

    private val _messages = MutableLiveData<List<ReceivedMessage>>()
    val messages: LiveData<List<ReceivedMessage>> = _messages

    private val _contactsById = MutableLiveData<Map<Long, Contact>>()
    val contactsById: LiveData<Map<Long, Contact>> = _contactsById

    init {
        refresh()
    }

    fun refresh() {
        _messages.value = dbHelper.getReceivedMessages()
        _contactsById.value = dbHelper.getContacts().associateBy { it.id }
    }
}
