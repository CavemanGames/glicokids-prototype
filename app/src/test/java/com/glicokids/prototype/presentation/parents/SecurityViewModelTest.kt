package com.glicokids.prototype.presentation.parents

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.glicokids.prototype.domain.repository.StorageRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SecurityViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val storageRepository = mockk<StorageRepository>(relaxed = true)
    private lateinit var viewModel: SecurityViewModel

    @Before
    fun setup() {
        every { storageRepository.getString(SecurityViewModel.KEY_PARENT_PIN, "") } returns "1234"
        viewModel = SecurityViewModel(storageRepository)
    }

    @Test
    fun `PIN vem do storage criptografado, nunca de constante no codigo`() {
        viewModel.validatePin("1234")

        verify { storageRepository.getString(SecurityViewModel.KEY_PARENT_PIN, "") }
        assertThat(viewModel.accessGranted.value).isTrue()
    }

    @Test
    fun `sem PIN salvo, semeia o PIN de teste do prototipo e o grava`() {
        every { storageRepository.getString(SecurityViewModel.KEY_PARENT_PIN, "") } returns ""
        val vm = SecurityViewModel(storageRepository)

        vm.validatePin("1234")

        verify { storageRepository.saveString(SecurityViewModel.KEY_PARENT_PIN, "1234") }
        assertThat(vm.accessGranted.value).isTrue()
    }

    @Test
    fun `PIN alterado no storage passa a valer e o antigo deixa de abrir`() {
        every { storageRepository.getString(SecurityViewModel.KEY_PARENT_PIN, "") } returns "9876"
        val vm = SecurityViewModel(storageRepository)

        vm.validatePin("1234")
        assertThat(vm.accessGranted.value).isFalse()

        vm.validatePin("9876")
        assertThat(vm.accessGranted.value).isTrue()
    }

    @Test
    fun `when entering correct PIN, should grant access`() {
        viewModel.validatePin("1234")
        
        assertThat(viewModel.accessGranted.value).isTrue()
        assertThat(viewModel.errorMessage.value).isNull()
    }

    @Test
    fun `when entering wrong PIN, should show error`() {
        viewModel.validatePin("0000")
        
        assertThat(viewModel.accessGranted.value).isFalse()
        assertThat(viewModel.errorMessage.value).isEqualTo("PIN Incorreto. 2 tentativas restantes.")
    }

    @Test
    fun `when entering empty PIN, should show error`() {
        viewModel.validatePin("")
        
        assertThat(viewModel.errorMessage.value).isEqualTo("Digite o PIN")
    }

    @Test
    fun `when entering wrong PIN 3 times, should lock account`() {
        viewModel.validatePin("0000")
        viewModel.validatePin("0000")
        viewModel.validatePin("0000")
        
        assertThat(viewModel.isLocked.value).isTrue()
        assertThat(viewModel.errorMessage.value).contains("Bloqueado")
    }

    @Test
    fun `when account is locked, should not allow further attempts`() {
        // Force lock
        repeat(3) { viewModel.validatePin("0000") }
        
        // Try correct PIN while locked
        viewModel.validatePin("1234")
        
        assertThat(viewModel.accessGranted.value).isFalse()
    }
}
