package com.glicokids.prototype.presentation.parents

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.glicokids.prototype.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _accessGranted = MutableLiveData(false)
    val accessGranted: LiveData<Boolean> = _accessGranted

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isLocked = MutableLiveData(false)
    val isLocked: LiveData<Boolean> = _isLocked

    private var attemptCount = 0
    private val maxAttempts = 3

    /**
     * §8.1: `parent_pin` is the only sensitive value and lives in EncryptedSharedPreferences.
     * On first run we seed the prototype test PIN (1234); from then on the storage is
     * the source of truth — never a constant in the code.
     */
    private val correctPin: String
        get() = storageRepository.getString(KEY_PARENT_PIN, "").ifBlank {
            storageRepository.saveString(KEY_PARENT_PIN, DEFAULT_PIN)
            DEFAULT_PIN
        }

    fun validatePin(pin: String) {
        if (_isLocked.value == true) {
            _errorMessage.value = "Bloqueado por segurança. Tente mais tarde."
            return
        }

        // Sanitization: Remove any non-numeric characters
        val cleanPin = pin.filter { it.isDigit() }

        if (cleanPin.isBlank()) {
            _errorMessage.value = "Digite o PIN"
            return
        }

        if (cleanPin == correctPin) {
            _errorMessage.value = null
            _accessGranted.value = true
            attemptCount = 0
        } else {
            attemptCount++
            if (attemptCount >= maxAttempts) {
                _isLocked.value = true
                _errorMessage.value = "Bloqueado após $maxAttempts tentativas."
            } else {
                val remaining = maxAttempts - attemptCount
                _errorMessage.value = "PIN Incorreto. $remaining tentativas restantes."
            }
            _accessGranted.value = false
        }
    }

    companion object {
        const val KEY_PARENT_PIN = "parent_pin"
        private const val DEFAULT_PIN = "1234"
    }
}
