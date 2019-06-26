package com.yubico.yubioath.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import com.yubico.yubikit.transport.YubiKeyTransport
import com.yubico.yubikit.transport.nfc.NfcTransport
import com.yubico.yubioath.R
import com.yubico.yubioath.client.Code
import com.yubico.yubioath.scancode.KeyboardLayout
import com.yubico.yubioath.ui.BaseActivity
import com.yubico.yubioath.ui.password.PasswordActivity
import com.yubico.yubioath.ui.settings.SettingsActivity
import org.jetbrains.anko.toast
import java.io.IOException

class MainActivity : BaseActivity<OathViewModel>(OathViewModel::class.java) {
    companion object {
        private const val REQUEST_PASSWORD = 2
        private const val MODHEX = "cbdefghijklnrtuv"
        val CODE_PATTERN = """(\d{6,8})|(!?[1-8$MODHEX${MODHEX.toUpperCase()}]{4}[$MODHEX]{28,60})""".toRegex()
    }

    private fun parseNdefData(data: ByteArray): Code {
        val dataString = String(data)
        return Code(if (CODE_PATTERN.matches(dataString)) {
            dataString
        } else {
            try {
                KeyboardLayout.forName(prefs.getString("keyboardLayout", KeyboardLayout.DEFAULT_NAME)!!)
            } catch (e: java.lang.IllegalArgumentException) {
                prefs.edit().putString("keyboardLayout", KeyboardLayout.DEFAULT_NAME).apply()
                KeyboardLayout.forName(KeyboardLayout.DEFAULT_NAME)
            }.fromScanCodes(data)
        }, System.currentTimeMillis(), Long.MAX_VALUE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Clear storage from older version of app
        getSharedPreferences("NEO_STORE", Context.MODE_PRIVATE).edit().clear().apply()

        if (prefs.getBoolean("readNdefData", false) && intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            (intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0] as NdefMessage).toByteArray()?.let { ndefData ->
                NfcTransport.parseNdefOtp(ndefData)?.let {
                    viewModel.ndefCode = parseNdefData(it)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchView = menu.findItem(R.id.menu_main_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean = false

            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.setSearchFilter(newText)
                return true
            }
        })

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_main_password).isEnabled = viewModel.deviceInfo.value!!.version.major > 0

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_main_password -> startActivityForResult(Intent(this, PasswordActivity::class.java), REQUEST_PASSWORD)
            R.id.menu_main_settings -> startActivity(Intent(this, SettingsActivity::class.java))
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_PASSWORD -> if (resultCode == Activity.RESULT_OK) {
                toast(R.string.password_updated)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onYubiKey(transport: YubiKeyTransport?) {
        if(transport == null) {
            viewModel.clearDevice()
            viewModel.clearCredentials()
        }
        super.onYubiKey(transport)
    }

    override suspend fun useTransport(transport: YubiKeyTransport) {
        if (prefs.getBoolean("readNdefData", false) && transport is NfcTransport) {
            try {
                transport.ndefBytes?.let {
                    viewModel.ndefCode = parseNdefData(it)
                }
            } catch (e: IOException) {
                Log.e("yubioath", "Error reading NDEF tag.", e)
            }
        }
        super.useTransport(transport)
    }

    override fun onPause() {
        viewModel.stopRefresh()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.scheduleRefresh()
    }
}
