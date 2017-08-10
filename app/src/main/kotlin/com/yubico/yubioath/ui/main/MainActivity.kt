package com.yubico.yubioath.ui.main

import android.os.Bundle
import com.yubico.yubioath.R
import com.yubico.yubioath.ui.BaseActivity

class MainActivity : BaseActivity<OathViewModel>(OathViewModel::class.java) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
