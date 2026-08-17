package com.glicokids.prototype.presentation.parents

import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.databinding.DialogContactBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * b20 — the one add/edit contact dialog, shared by every entry point that needs it:
 * "+ Adicionar pessoa" and "Editar" in [SupportNetworkActivity], and the "número fora
 * da rede" shortcut in [ReceivedMessagesActivity]. One dialog means one set of
 * validation rules — [SupportNetworkViewModel.validateContact] stays the only authority
 * regardless of which screen opened it.
 */
object ContactDialog {

    /**
     * @param initialPhone pre-fills the phone field for a new contact (the "add unknown
     * sender" shortcut from b23); ignored when [existing] is not null.
     */
    fun show(
        activity: AppCompatActivity,
        viewModel: SupportNetworkViewModel,
        existing: Contact?,
        initialPhone: String? = null,
        onSaved: () -> Unit = {}
    ) {
        val dialogBinding = DialogContactBinding.inflate(activity.layoutInflater)

        if (existing != null) {
            dialogBinding.etContactName.setText(existing.name)
            dialogBinding.etContactPhone.setText(existing.phone)
            dialogBinding.etContactEmail.setText(existing.email.orEmpty())
            dialogBinding.swDlgReceivesAlert.isChecked = existing.receivesAlert
            dialogBinding.swDlgReceivesReport.isChecked = existing.receivesReport
            dialogBinding.cgRelationship.check(chipIdFor(dialogBinding, existing.relationship))
        } else {
            dialogBinding.cgRelationship.check(dialogBinding.chipMother.id)
            if (!initialPhone.isNullOrBlank()) dialogBinding.etContactPhone.setText(initialPhone)
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) "Adicionar pessoa" else "Editar pessoa")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()

        dialog.show()
        val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        fun refreshSaveEnabled() {
            saveButton.isEnabled = isFormValid(viewModel, dialogBinding)
        }

        // Defect A (dialog path) — swDlgReceivesReport used to only feed refreshSaveEnabled,
        // so it could stay checked+enabled with an empty e-mail field (Save just never fired).
        // Disabling it here, driven by the same canReceiveReport() the row-switch guard uses,
        // closes that path too; a switch that becomes disabled while checked is turned off so
        // the on-screen state never lies about what would be persisted.
        fun updateReportSwitchEnabled() {
            val canReceive = viewModel.canReceiveReport(dialogBinding.etContactEmail.text?.toString())
            dialogBinding.swDlgReceivesReport.isEnabled = canReceive
            if (!canReceive && dialogBinding.swDlgReceivesReport.isChecked) {
                dialogBinding.swDlgReceivesReport.isChecked = false
            }
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                clearDialogErrors(dialogBinding)
                // Defect B — validateContact() already computes the right field+message;
                // it just used to be called only from the Save click, which never fired while
                // the button stayed disabled. Calling it on every keystroke turns the dead
                // applyDialogError() path into live inline validation.
                applyDialogError(
                    dialogBinding,
                    viewModel.validateContact(
                        name = dialogBinding.etContactName.text?.toString().orEmpty(),
                        phone = dialogBinding.etContactPhone.text?.toString().orEmpty(),
                        email = dialogBinding.etContactEmail.text?.toString(),
                        receivesReport = dialogBinding.swDlgReceivesReport.isChecked
                    )
                )
                updateReportSwitchEnabled()
                refreshSaveEnabled()
            }
        }
        dialogBinding.etContactName.addTextChangedListener(watcher)
        dialogBinding.etContactPhone.addTextChangedListener(watcher)
        dialogBinding.etContactEmail.addTextChangedListener(watcher)
        dialogBinding.swDlgReceivesReport.setOnCheckedChangeListener { _, _ -> refreshSaveEnabled() }

        // Corrects a legacy invalid state on open too (a contact saved before this fix could
        // already have receivesReport=true with no e-mail).
        updateReportSwitchEnabled()
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
                onSaved()
            } else {
                applyDialogError(dialogBinding, viewModel.validationError.value)
            }
        }
    }

    /**
     * Delegates to [SupportNetworkViewModel.validateContact] — the one authority for the
     * rules — only to drive the Save button's enabled state in real time as the user types.
     */
    private fun isFormValid(viewModel: SupportNetworkViewModel, b: DialogContactBinding): Boolean {
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
}
