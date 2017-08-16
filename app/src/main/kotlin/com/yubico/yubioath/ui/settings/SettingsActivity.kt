package com.yubico.yubioath.ui.settings

import android.os.Bundle
import com.yubico.yubioath.R
import com.yubico.yubioath.ui.BaseActivity

/**
 * Created by Dain on 2017-08-15.
 */
class SettingsActivity : BaseActivity<SettingsViewModel>(SettingsViewModel::class.java) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }
}