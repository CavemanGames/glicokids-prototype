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

/** Which form field a [ContactValidationError] belongs to, so a caller can route the
 * message to the right input without pattern-matching on the message text. */
enum class ContactValidationField { NAME, PHONE, EMAIL }

/** Result of [SupportNetworkViewModel.validateContact]: the failing field plus the
 * pt-BR message to show the user. */
data class ContactValidationError(val field: ContactValidationField, val message: String)

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

    private val _validationError = MutableLiveData<ContactValidationError?>()
    val validationError: LiveData<ContactValidationError?> = _validationError

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
        val error = validateContact(name, phone, email, receivesReport)
        if (error != null) {
            _validationError.value = error
            return false
        }

        val trimmedEmail = email?.trim().orEmpty()
        val contact = Contact(
            id = existing?.id ?: 0L,
            name = name.trim(),
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

    /**
     * Single authority for contact validation. [saveContact] calls it to decide whether to
     * persist and what to publish in [validationError]; [SupportNetworkActivity] calls the
     * exact same function to drive the Save button's live enabled state as the user types.
     * Pure and side-effect free, so both callers always agree — a rule change here can never
     * leave the button enabled on an entry the ViewModel then refuses.
     */
    fun validateContact(
        name: String,
        phone: String,
        email: String?,
        receivesReport: Boolean
    ): ContactValidationError? {
        val trimmedName = name.trim()
        val phoneDigits = phone.count { it.isDigit() }
        val trimmedEmail = email?.trim().orEmpty()

        return when {
            trimmedName.isEmpty() ->
                ContactValidationError(ContactValidationField.NAME, "Informe um nome")
            phoneDigits !in 10..11 ->
                ContactValidationError(
                    ContactValidationField.PHONE,
                    "Informe um celular com DDD (10 ou 11 dígitos)"
                )
            trimmedEmail.isNotEmpty() && !isValidEmail(trimmedEmail) ->
                ContactValidationError(ContactValidationField.EMAIL, "Informe um e-mail válido")
            receivesReport && trimmedEmail.isEmpty() ->
                ContactValidationError(
                    ContactValidationField.EMAIL,
                    "O relatório por e-mail exige um endereço"
                )
            else -> null
        }
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

    /**
     * Defect A fix — this is the path that used to write straight to SQLite with no
     * validation ([ContactAdapter]'s row switch, unlike the dialog's Save button which
     * already went through [validateContact]). Turning the report ON for a contact with
     * no e-mail is refused and never reaches [GlicoKidsDbHelper.updateContact]; turning it
     * OFF is always allowed. Returns false so the caller (the Activity) knows to revert
     * the switch it already flipped on screen.
     */
    fun setReceivesReport(contact: Contact, enabled: Boolean): Boolean {
        if (enabled && !canReceiveReport(contact.email)) {
            _validationError.value = ContactValidationError(
                ContactValidationField.EMAIL,
                "O relatório por e-mail exige um endereço"
            )
            return false
        }
        dbHelper.updateContact(contact.copy(receivesReport = enabled))
        _validationError.value = null
        refresh()
        return true
    }

    /**
     * Whether a contact can have [Contact.receivesReport] turned on — an e-mail that is
     * non-null and non-blank after trimming. Shared by [setReceivesReport]'s guard and by
     * [ContactDialog], which uses it to disable `swDlgReceivesReport` itself instead of
     * only gating the Save button (defect A/B).
     */
    fun canReceiveReport(email: String?): Boolean = !email?.trim().isNullOrEmpty()

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

    /**
     * Requirement 2 — who the manual "share summary" SMS is addressed to: the primary
     * contact, falling back to the first registered one because onboarding (b3) never
     * ran and no contact is marked primary yet (see TODO(onboarding) in
     * [GlicoKidsDbHelper]). Null only when the network has no contact at all.
     */
    fun getSummaryRecipientPhone(): String? {
        val list = _contacts.value ?: return null
        return list.firstOrNull { it.isPrimary }?.phone ?: list.firstOrNull()?.phone
    }

    private fun isValidEmail(email: String): Boolean = EMAIL_PATTERN.matches(email)

    companion object {
        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
