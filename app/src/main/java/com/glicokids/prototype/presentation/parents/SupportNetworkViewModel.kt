package com.glicokids.prototype.presentation.parents

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.local.ReportStorage
import com.glicokids.prototype.data.model.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * b19/b20 — Support network. Owns contact validation and persistence; the primary
 * contact's own removal guard lives in [GlicoKidsDbHelper.deleteContact] alone, this
 * class never re-checks `isPrimary` before delegating. The 7-day summary reuses
 * [ReportStorage] instead of a second report generator (requirement 2).
 */
@HiltViewModel
class SupportNetworkViewModel @Inject constructor(
    private val dbHelper: GlicoKidsDbHelper,
    private val reportStorage: ReportStorage
) : ViewModel() {

    private val _contacts = MutableLiveData<List<Contact>>()
    val contacts: LiveData<List<Contact>> = _contacts

    private val _validationError = MutableLiveData<String?>()
    val validationError: LiveData<String?> = _validationError

    private val _summaryText = MutableLiveData<String?>()
    val summaryText: LiveData<String?> = _summaryText

    init {
        refresh()
    }

    private fun refresh() {
        _contacts.value = dbHelper.getContacts()
    }

    /**
     * Validates and persists one contact. New contacts go through [GlicoKidsDbHelper.insertContact];
     * editing an [existing] one preserves its `id`/`isPrimary`/`createdAt` and goes through
     * [GlicoKidsDbHelper.updateContact]. Returns `false` without touching the database when
     * validation fails — [validationError] then carries the reason.
     */
    fun saveContact(
        existing: Contact?,
        name: String,
        relationship: String,
        phone: String,
        email: String?,
        receivesAlert: Boolean,
        receivesReport: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val trimmedName = name.trim()
        val phoneDigits = phone.count { it.isDigit() }
        val trimmedEmail = email?.trim().orEmpty()

        if (trimmedName.isEmpty()) {
            _validationError.value = "Informe um nome"
            return false
        }
        if (phoneDigits !in 10..11) {
            _validationError.value = "Informe um celular com DDD (10 ou 11 dígitos)"
            return false
        }
        if (trimmedEmail.isNotEmpty() && !isValidEmail(trimmedEmail)) {
            _validationError.value = "Informe um e-mail válido"
            return false
        }
        if (receivesReport && trimmedEmail.isEmpty()) {
            _validationError.value = "O relatório por e-mail exige um endereço"
            return false
        }

        val contact = Contact(
            id = existing?.id ?: 0L,
            name = trimmedName,
            relationship = relationship,
            phone = phone,
            email = trimmedEmail.takeIf { it.isNotEmpty() },
            receivesAlert = receivesAlert,
            receivesReport = receivesReport,
            isPrimary = existing?.isPrimary ?: false,
            createdAt = existing?.createdAt ?: nowMillis
        )

        if (existing == null) {
            dbHelper.insertContact(contact)
        } else {
            dbHelper.updateContact(contact)
        }
        _validationError.value = null
        refresh()
        return true
    }

    /** Delegates straight to the helper — the primary-contact guard lives there alone. */
    fun removeContact(contact: Contact) {
        dbHelper.deleteContact(contact.id)
        refresh()
    }

    fun setReceivesAlert(contact: Contact, enabled: Boolean) {
        dbHelper.updateContact(contact.copy(receivesAlert = enabled))
        refresh()
    }

    fun setReceivesReport(contact: Contact, enabled: Boolean) {
        dbHelper.updateContact(contact.copy(receivesReport = enabled))
        refresh()
    }

    /**
     * Requirement 2 — builds the same 7-day summary as the Parent Area report, through
     * [ReportStorage.buildReport], and publishes it in [summaryText] for the Activity to
     * hand off to an SMS app. Off the main thread, same as every other disk-touching call.
     */
    fun shareSummary(nowMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { reportStorage.buildReport(nowMillis) }
            _summaryText.value = text
        }
    }

    private fun isValidEmail(email: String): Boolean = EMAIL_PATTERN.matches(email)

    companion object {
        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
