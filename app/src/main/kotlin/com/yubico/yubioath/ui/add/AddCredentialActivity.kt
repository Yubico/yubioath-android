package com.yubico.yubioath.ui.add

import android.os.Bundle
import com.yubico.yubioath.R
import com.yubico.yubioath.ui.BaseActivity

/**
 * Created by Dain on 2017-08-10.
 */
class AddCredentialActivity : BaseActivity<AddCredentialViewModel>(AddCredentialViewModel::class.java) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_credential)

        intent.data?.let {
            viewModel.handleScanResults(it)
        }
    }
}