package com.yubico.yubioath.ui.add

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.yubico.yubioath.R
import com.yubico.yubioath.ui.BaseActivity
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch

/**
 * Created by Dain on 2017-08-10.
 */
class AddCredentialActivity : BaseActivity<AddCredentialViewModel>(AddCredentialViewModel::class.java) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_credential)

        supportActionBar?.apply {
            setTitle(R.string.new_credential)
            setHomeAsUpIndicator(R.drawable.ic_close_black_24dp)
            setDisplayHomeAsUpEnabled(true)
        }

        intent.data?.let {
            viewModel.handleScanResults(it)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_add_credential, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.menu_add_credential_save -> (supportFragmentManager.findFragmentById(R.id.fragment) as AddCredentialFragment).apply {
                val data = validateData()
                if (data != null) {
                    Log.d("yubioath", "Data: $data")
                    if (viewModel.lastDeviceInfo.persistent) {
                        viewModel.requestClient(viewModel.lastDeviceInfo.id) { client ->
                            client.addCredential(data)
                        }.invokeOnCompletion {
                            launch(UI) {
                                finish()
                            }
                        }
                    } else {
                        Log.d("yubioath", "TODO: Tap dialog")
                    }
                } else {
                    Log.d("yubioath", "Errors!")
                }
            }
        }
        return true
    }
}