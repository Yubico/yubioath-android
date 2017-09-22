package com.yubico.yubioath.ui.password

import com.yubico.yubioath.ui.BaseViewModel
import kotlinx.coroutines.experimental.Deferred

class PasswordViewModel : BaseViewModel() {
    fun setPassword(oldPassword: String, newPassword: String, remember: Boolean): Deferred<Boolean> = requestClient(lastDeviceInfo.id) {
        it.setPassword(oldPassword, newPassword, remember)
    }
}