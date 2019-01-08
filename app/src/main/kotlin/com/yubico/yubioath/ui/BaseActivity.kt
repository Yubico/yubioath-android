package com.yubico.yubioath.ui

import android.content.Intent
import android.content.SharedPreferences
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager
import com.yubico.yubikit.DeviceManager
import com.yubico.yubioath.R
import com.yubico.yubikit.transport.Iso7816Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.jetbrains.anko.toast
import kotlin.coroutines.CoroutineContext

abstract class BaseActivity<T : BaseViewModel>(private var modelClass: Class<T>) : AppCompatActivity(), CoroutineScope {
    protected lateinit var viewModel: T
    private lateinit var deviceManager: DeviceManager
    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        job = Job()

        if (prefs.getBoolean("hideThumbnail", true)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        viewModel = ViewModelProviders.of(this).get(modelClass)
        deviceManager = DeviceManager(this, BaseViewModel.HANDLER) {
            it.enableReaderMode(prefs.getBoolean("useNfcReaderMode", false)).enableUnavailableNfcUserPrompt(false)
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        viewModel.start(this, deviceManager)
        deviceManager.interceptIntent(intent)
    }


    public override fun onPause() {
        super.onPause()
        deviceManager.setOnYubiKeyHandler(null)
        viewModel.stop()
    }

    public override fun onResume() {
        super.onResume()
        viewModel.start(this, deviceManager)
        deviceManager.setOnYubiKeyHandler(Iso7816Backend::class.java, viewModel::onBackend)

        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED && !viewModel.ndefConsumed) {
            if (prefs.getBoolean("readNdefData", false)) {
                viewModel.ndefIntentData = (intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0] as NdefMessage).toByteArray()
            }
            viewModel.ndefConsumed = true
            deviceManager.interceptIntent(intent)
        }

        if (prefs.getBoolean("warnNfc", true) && !viewModel.nfcWarned) {
            when(val adapter = NfcAdapter.getDefaultAdapter(this)) {
                null -> R.string.no_nfc
                else -> if (!adapter.isEnabled) R.string.nfc_off else null
            }?.let {
                toast(it)
                viewModel.nfcWarned = true
            }
        }
    }
}