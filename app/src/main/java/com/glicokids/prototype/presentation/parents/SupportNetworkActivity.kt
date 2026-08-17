package com.glicokids.prototype.presentation.parents

import android.Manifest
import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.glicokids.prototype.R
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.databinding.ActivitySupportNetworkBinding
import com.glicokids.prototype.domain.repository.SmsGateway
import com.glicokids.prototype.util.UIHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        binding.btnAddContact.setOnClickListener { ContactDialog.show(this, viewModel, existing = null) }

        binding.rowAlertSettings.setOnClickListener {
            UIHelper.navigateTo(this, AlertSettingsActivity::class.java)
        }

        binding.rowShareSummary.setOnClickListener {
            awaitingSummaryShare = true
            viewModel.shareSummary()
        }

        binding.rowReceivedMessages.setOnClickListener {
            UIHelper.navigateTo(this, ReceivedMessagesActivity::class.java)
        }
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
                val phone = viewModel.getSummaryRecipientPhone()
                if (phone == null) {
                    UIHelper.showToast(this, "Cadastre um contato na rede de apoio para enviar o resumo")
                } else {
                    val sent = UIHelper.sendSmsViaMessagingApp(this, phone, text)
                    if (!sent) UIHelper.showToast(this, "Nenhum aplicativo de SMS encontrado no aparelho")
                }
            }
        }

        viewModel.receivedMessageCount.observe(this) { count ->
            binding.tvReceivedCount.text = "$count ›"
        }
    }

    private fun onAlertToggled(contact: Contact, enabled: Boolean) {
        viewModel.setReceivesAlert(contact, enabled)
        binding.root.announceForAccessibility(
            "SMS de alerta para ${contact.name} ${if (enabled) "ligado" else "desligado"}"
        )
    }

    private fun onReportToggled(contact: Contact, enabled: Boolean) {
        val accepted = viewModel.setReceivesReport(contact, enabled)
        if (!accepted) {
            // Refused (defect A): the contact has no e-mail. The row's own list of
            // contacts was never mutated, so redrawing it snaps the switch back to
            // contact.receivesReport instead of leaving it checked on screen while
            // the database stayed untouched.
            contactAdapter.notifyDataSetChanged()
            UIHelper.showToast(this, "Cadastre um e-mail para ${contact.name} antes de ligar o relatório")
            return
        }
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
                ContactDialog.show(this, viewModel, existing = contact)
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
        // sendTextMessage is suspend — it waits for the carrier's real
        // confirmation instead of assuming a send worked — so this hops off the main thread
        // the same way NewMealActivity does for its own IO-bound work.
        lifecycleScope.launch {
            val sent = withContext(Dispatchers.IO) { smsGateway.sendTextMessage(contact.phone, message) }
            UIHelper.showToast(
                this@SupportNetworkActivity,
                if (sent) "SMS de teste enviado para ${contact.name}" else "Não foi possível enviar o SMS de teste"
            )
        }
    }

    private fun confirmRemoveContact(contact: Contact) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remover ${contact.name}?")
            .setMessage("Essa pessoa deixa de receber alertas e relatórios do GlicoKids.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Remover") { _, _ -> viewModel.removeContact(contact) }
            .show()
    }

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_TEST = 2
        private const val MENU_REMOVE = 3
    }
}
