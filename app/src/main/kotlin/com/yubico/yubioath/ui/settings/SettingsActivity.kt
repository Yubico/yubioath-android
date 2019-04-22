package com.yubico.yubioath.ui.settings

import android.os.Bundle
import androidx.lifecycle.Observer
import com.yubico.yubioath.R
import com.yubico.yubioath.ui.BaseActivity
import org.jetbrains.anko.toast

class SettingsActivity : BaseActivity<SettingsViewModel>(SettingsViewModel::class.java) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        viewModel.clearPasswords.observe(this, Observer {
            if (it) {
                keyManager.clearAll()
                viewModel.setClearPasswords(false)
                toast(R.string.passwords_cleared)
            }
        })
    }
}