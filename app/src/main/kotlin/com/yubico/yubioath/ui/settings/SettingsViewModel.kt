package com.yubico.yubioath.ui.settings

import com.yubico.yubioath.R
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.protocol.YkOathApi
import com.yubico.yubioath.ui.BaseViewModel
import com.yubico.yubioath.ui.main.IconManager
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.toast

class SettingsViewModel : BaseViewModel() {
    var onDeviceRefresh: ((YkOathApi.DeviceInfo) -> Unit)? = null

    fun clearStoredPasswords() {
        services?.let {
            it.keyManager.clearAll()
            launch {
                it.context.toast(R.string.passwords_cleared)
            }
        }
    }

    fun clearIcons() {
        services?.let {
            IconManager(it.context).clearIcons()
            launch {
                it.context.toast(R.string.icons_cleared)
            }
        }
    }

    override suspend fun useClient(client: OathClient) {
        onDeviceRefresh?.let {
            launch {
                it.invoke(client.deviceInfo)
            }
        }
    }
}