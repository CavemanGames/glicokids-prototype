package com.glicokids.prototype.presentation.kids

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.glicokids.prototype.data.local.AppPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AvatarViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val prefs = mockk<AppPreferences>(relaxed = true)
    private lateinit var viewModel: AvatarViewModel

    @Before
    fun setup() {
        every { prefs.avatarIndex } returns 2
        viewModel = AvatarViewModel(prefs)
    }

    @Test
    fun `carrega o avatar salvo nas SharedPreferences`() {
        assertThat(viewModel.currentIndex.value).isEqualTo(2)
    }

    @Test
    fun `avancar a partir do ultimo volta para o primeiro`() {
        every { prefs.avatarIndex } returns 4
        val vm = AvatarViewModel(prefs)

        vm.nextAvatar()
        assertThat(vm.currentIndex.value).isEqualTo(0)
    }

    @Test
    fun `voltar a partir do primeiro vai para o ultimo`() {
        every { prefs.avatarIndex } returns 0
        val vm = AvatarViewModel(prefs)

        vm.previousAvatar()
        assertThat(vm.currentIndex.value).isEqualTo(4)
    }

    @Test
    fun `escolher avatar persiste o indice para as outras Activities lerem`() {
        viewModel.selectAvatar(3)

        verify { prefs.avatarIndex = 3 }
    }
}
