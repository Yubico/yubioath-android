package com.yubico.yubioath.ui.settings

import com.yubico.yubioath.R
import com.yubico.yubioath.ui.BaseViewModel
import com.yubico.yubioath.ui.main.IconManager
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.toast

class SettingsViewModel : BaseViewModel() {
    fun clearStoredPasswords() {
        services?.let {
            it.keyManager.clearAll()
            launch(UI) {
                it.context.toast(R.string.passwords_cleared)
            }
        }
    }

    fun clearIcons() {
        services?.let {
            IconManager(it.context).clearIcons()
            launch(UI) {
                it.context.toast(R.string.icons_cleared)
            }
        }
    }
}