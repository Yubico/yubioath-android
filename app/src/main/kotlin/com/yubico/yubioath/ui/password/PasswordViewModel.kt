package com.yubico.yubioath.ui.password

import com.yubico.yubioath.ui.BaseViewModel

class PasswordViewModel: BaseViewModel() {
    fun setPassword(pw: String, remember:Boolean) = requestClient(lastDeviceInfo.id) {
        it.setPassword(pw, remember)
    }
}