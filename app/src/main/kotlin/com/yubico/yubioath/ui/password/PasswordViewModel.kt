package com.yubico.yubioath.ui.password

import com.yubico.yubioath.ui.BaseViewModel

class PasswordViewModel : BaseViewModel() {
    fun setPassword(oldPassword: String, newPassword: String, remember: Boolean) = requestClient(deviceInfo.value!!.id) {
        it.setPassword(oldPassword, newPassword, remember)
    }
}