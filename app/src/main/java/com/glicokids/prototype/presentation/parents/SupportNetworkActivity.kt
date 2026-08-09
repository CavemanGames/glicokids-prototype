package com.glicokids.prototype.presentation.parents

import android.Manifest
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import com.glicokids.prototype.R
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.databinding.ActivitySupportNetworkBinding
import com.glicokids.prototype.databinding.DialogContactBinding
import com.glicokids.prototype.domain.repository.SmsGateway
import com.glicokids.prototype.util.UIHelper
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * b19/b20 — Support network. Protected by PIN simply by living inside the Parent Area
 * (opened from [ParentAreaFragment.cvSupportNetwork]).
 */
@AndroidEntryPoint
class SupportNetworkActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupportNetworkBinding
    private lateinit var contactAdapter: ContactAdapter
    private val viewModel: SupportNetworkViewModel by viewModels()

    @Inject lateinit var smsGateway: SmsGateway

    private var contacts: List<Contact> = emptyList()
    private var awaitingSummaryShare = false

    /**
     * SEND_SMS, RECEIVE_SMS and POST_NOTIFICATIONS asked together, once, on open — the
     * screen never re-prompts and a denial only shows an explanatory toast; every row
     * stays clickable either way.
     */
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.any { granted -> !granted }) {
                UIHelper.showToast(
                    this,
                    "Sem essas permissões o app não consegue enviar SMS nem avisar sobre mensagens recebidas"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupportNetworkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )

        contactAdapter = ContactAdapter(
            context = this,
            onMoreOptionsClick = { anchor, contact -> showContactOptions(anchor, contact) },
            onAlertToggle = ::onAlertToggled,
            onReportToggle = ::onReportToggled
        )
        binding.lvContacts.adapter = contactAdapter
        registerForContextMenu(binding.lvContacts)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddContact.setOnClickListener { showContactDialog(null) }

        binding.rowAlertSettings.setOnClickListener {
            UIHelper.navigateTo(this, AlertSettingsActivity::class.java)
        }

        binding.rowShareSummary.setOnClickListener {
            awaitingSummaryShare = true
            viewModel.shareSummary()
        }

        // b23 (the inbox screen) does not exist yet — left honestly inert instead of
        // pointing the tap at a destination that is not there.
        binding.rowReceivedMessages.isEnabled = false
        binding.rowReceivedMessages.alpha = 0.5f
        binding.rowReceivedMessages.contentDescription = "Transmissões recebidas, disponível em breve"
        binding.tvReceivedCount.text = "em breve"
    }

    private fun observeViewModel() {
        viewModel.contacts.observe(this) { list ->
            contacts = list
            contactAdapter.submit(list)
            binding.tvEmptyContacts.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.lvContacts.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.summaryText.observe(this) { text ->
            if (text != null && awaitingSummaryShare) {
                awaitingSummaryShare = false
                val sent = UIHelper.sendSmsViaMessagingApp(this, "", text)
                if (!sent) UIHelper.showToast(this, "Nenhum aplicativo de SMS encontrado no aparelho")
            }
        }
    }

    private fun onAlertToggled(contact: Contact, enabled: Boolean) {
        viewModel.setReceivesAlert(contact, enabled)
        binding.root.announceForAccessibility(
            "SMS de alerta para ${contact.name} ${if (enabled) "ligado" else "desligado"}"
        )
    }

    private fun onReportToggled(contact: Contact, enabled: Boolean) {
        viewModel.setReceivesReport(contact, enabled)
        binding.root.announceForAccessibility(
            "Relatório para ${contact.name} ${if (enabled) "ligado" else "desligado"}"
        )
    }

    // ------------------------------------------------------------------
    // Contact context menu — reachable both by the visible "⋮" and by long-press,
    // both funnelling into handleContactAction so there is only one place that
    // decides what each item does.
    // ------------------------------------------------------------------

    private fun showContactOptions(anchor: View, contact: Contact) {
        val popupContext = ContextThemeWrapper(this, R.style.ThemeOverlay_GlicoKids_Popup)
        PopupMenu(popupContext, anchor).apply {
            menu.add(0, MENU_EDIT, 0, "Editar")
            menu.add(0, MENU_TEST, 1, "Enviar teste")
            if (!contact.isPrimary) menu.add(0, MENU_REMOVE, 2, "Remover")
            setOnMenuItemClickListener { item -> handleContactAction(item.itemId, contact) }
        }.show()
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val info = menuInfo as? AdapterView.AdapterContextMenuInfo ?: return
        val contact = contacts.getOrNull(info.position) ?: return

        menu?.setHeaderTitle(contact.name)
        menu?.add(0, MENU_EDIT, 0, "Editar")
        menu?.add(0, MENU_TEST, 1, "Enviar teste")
        if (!contact.isPrimary) menu?.add(0, MENU_REMOVE, 2, "Remover")
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val info = item.menuInfo as? AdapterView.AdapterContextMenuInfo
            ?: return super.onContextItemSelected(item)
        val contact = contacts.getOrNull(info.position) ?: return super.onContextItemSelected(item)

        return handleContactAction(item.itemId, contact) || super.onContextItemSelected(item)
    }

    /** Single source of truth for both entry points (visible "⋮" and long-press). */
    private fun handleContactAction(itemId: Int, contact: Contact): Boolean {
        return when (itemId) {
            MENU_EDIT -> {
                showContactDialog(contact)
                true
            }
            MENU_TEST -> {
                sendTestMessage(contact)
                true
            }
            MENU_REMOVE -> {
                confirmRemoveContact(contact)
                true
            }
            else -> false
        }
    }

    private fun sendTestMessage(contact: Contact) {
        val message = "GlicoKids: mensagem de teste para ${contact.name}. Sua rede de apoio está configurada."
        val sent = smsGateway.sendTextMessage(contact.phone, message)
        UIHelper.showToast(
            this,
            if (sent) "SMS de teste enviado para ${contact.name}" else "Não foi possível enviar o SMS de teste"
        )
    }

    private fun confirmRemoveContact(contact: Contact) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remover ${contact.name}?")
            .setMessage("Essa pessoa deixa de receber alertas e relatórios do GlicoKids.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Remover") { _, _ -> viewModel.removeContact(contact) }
            .show()
    }

    // ------------------------------------------------------------------
    // b20 — the one add/edit dialog, shared by "+ Adicionar pessoa" and "Editar"
    // ------------------------------------------------------------------

    private fun showContactDialog(existing: Contact?) {
        val dialogBinding = DialogContactBinding.inflate(layoutInflater)

        if (existing != null) {
            dialogBinding.etContactName.setText(existing.name)
            dialogBinding.etContactPhone.setText(existing.phone)
            dialogBinding.etContactEmail.setText(existing.email.orEmpty())
            dialogBinding.swDlgReceivesAlert.isChecked = existing.receivesAlert
            dialogBinding.swDlgReceivesReport.isChecked = existing.receivesReport
            dialogBinding.cgRelationship.check(chipIdFor(dialogBinding, existing.relationship))
        } else {
            dialogBinding.cgRelationship.check(dialogBinding.chipMother.id)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Adicionar pessoa" else "Editar pessoa")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()

        dialog.show()
        val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        fun refreshSaveEnabled() {
            saveButton.isEnabled = isFormValid(dialogBinding)
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                clearDialogErrors(dialogBinding)
                refreshSaveEnabled()
            }
        }
        dialogBinding.etContactName.addTextChangedListener(watcher)
        dialogBinding.etContactPhone.addTextChangedListener(watcher)
        dialogBinding.etContactEmail.addTextChangedListener(watcher)
        dialogBinding.swDlgReceivesReport.setOnCheckedChangeListener { _, _ -> refreshSaveEnabled() }

        refreshSaveEnabled()

        saveButton.setOnClickListener {
            val saved = viewModel.saveContact(
                existing = existing,
                name = dialogBinding.etContactName.text.toString(),
                relationship = selectedRelationshipLabel(dialogBinding),
                phone = dialogBinding.etContactPhone.text.toString(),
                email = dialogBinding.etContactEmail.text.toString(),
                receivesAlert = dialogBinding.swDlgReceivesAlert.isChecked,
                receivesReport = dialogBinding.swDlgReceivesReport.isChecked
            )
            if (saved) {
                dialog.dismiss()
            } else {
                applyDialogError(dialogBinding, viewModel.validationError.value)
            }
        }
    }

    /**
     * Delegates to [SupportNetworkViewModel.validateContact] — the one authority for the
     * rules — only to drive the Save button's enabled state in real time as the user types.
     */
    private fun isFormValid(b: DialogContactBinding): Boolean {
        return viewModel.validateContact(
            name = b.etContactName.text?.toString().orEmpty(),
            phone = b.etContactPhone.text?.toString().orEmpty(),
            email = b.etContactEmail.text?.toString(),
            receivesReport = b.swDlgReceivesReport.isChecked
        ) == null
    }

    private fun clearDialogErrors(b: DialogContactBinding) {
        b.tilContactName.error = null
        b.tilContactPhone.error = null
        b.tilContactEmail.error = null
    }

    private fun applyDialogError(b: DialogContactBinding, error: ContactValidationError?) {
        error ?: return
        when (error.field) {
            ContactValidationField.NAME -> b.tilContactName.error = error.message
            ContactValidationField.PHONE -> b.tilContactPhone.error = error.message
            ContactValidationField.EMAIL -> b.tilContactEmail.error = error.message
        }
    }

    private fun selectedRelationshipLabel(b: DialogContactBinding): String {
        val chip = b.cgRelationship.findViewById<Chip>(b.cgRelationship.checkedChipId)
        return chip?.text?.toString() ?: b.chipOther.text.toString()
    }

    private fun chipIdFor(b: DialogContactBinding, relationship: String): Int {
        val chips = listOf(
            b.chipMother, b.chipFather, b.chipGrandmother, b.chipGrandfather,
            b.chipUncle, b.chipCaregiver, b.chipDoctor, b.chipSchool, b.chipOther
        )
        return chips.firstOrNull { it.text == relationship }?.id ?: b.chipOther.id
    }

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_TEST = 2
        private const val MENU_REMOVE = 3
    }
}
