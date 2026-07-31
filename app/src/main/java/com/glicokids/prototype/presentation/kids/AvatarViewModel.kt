package com.glicokids.prototype.presentation.kids

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.glicokids.prototype.data.local.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Module 5 — requirement 2: the avatar picked here is read by the home screen,
 * in another Activity, through the same [AppPreferences] (`getSharedPreferences`).
 */
@HiltViewModel
class AvatarViewModel @Inject constructor(
    private val prefs: AppPreferences
) : ViewModel() {

    private val _currentIndex = MutableLiveData<Int>()
    val currentIndex: LiveData<Int> = _currentIndex

    private val avatarCount = 5

    init {
        _currentIndex.value = prefs.avatarIndex
    }

    fun nextAvatar() {
        val current = _currentIndex.value ?: 0
        _currentIndex.value = (current + 1) % avatarCount
    }

    fun previousAvatar() {
        val current = _currentIndex.value ?: 0
        _currentIndex.value = if (current == 0) avatarCount - 1 else current - 1
    }

    fun selectAvatar(index: Int) {
        prefs.avatarIndex = index
    }
}
