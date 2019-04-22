package com.yubico.yubioath.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.yubico.yubioath.ui.BaseViewModel

class SettingsViewModel : BaseViewModel() {
    private val _clearPasswords = MutableLiveData<Boolean>()
    val clearPasswords: LiveData<Boolean> = _clearPasswords
    fun setClearPasswords(value: Boolean) {
        _clearPasswords.value = value
    }
}