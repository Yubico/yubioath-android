package com.yubico.yubioath.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager
import com.yubico.yubikit.YubiKitManager
import com.yubico.yubikit.application.ApduException
import com.yubico.yubikit.application.oath.OathApplication
import com.yubico.yubikit.transport.OnYubiKeyListener
import com.yubico.yubikit.transport.YubiKeyTransport
import com.yubico.yubikit.transport.nfc.NordpolNfcDispatcher
import com.yubico.yubioath.R
import com.yubico.yubioath.client.KeyManager
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.exc.PasswordRequiredException
import com.yubico.yubioath.keystore.ClearingMemProvider
import com.yubico.yubioath.keystore.KeyStoreProvider
import com.yubico.yubioath.keystore.SharedPrefProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.jetbrains.anko.toast
import kotlin.coroutines.CoroutineContext

abstract class BaseActivity<T : BaseViewModel>(private var modelClass: Class<T>) : AppCompatActivity(), CoroutineScope, OnYubiKeyListener {
    companion object {
        private const val SP_STORED_AUTH_KEYS = "com.yubico.yubioath.SP_STORED_AUTH_KEYS"

        private val MEM_STORE = ClearingMemProvider()
    }

    protected lateinit var viewModel: T
    protected val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var yubiKitManager: YubiKitManager
    private lateinit var nfcDispatcher: NordpolNfcDispatcher
    private lateinit var exec: CoroutineDispatcher

    protected val keyManager: KeyManager by lazy {
        KeyManager(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    KeyStoreProvider()
                } else {
                    SharedPrefProvider(getSharedPreferences(SP_STORED_AUTH_KEYS, Context.MODE_PRIVATE))
                },
                MEM_STORE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        job = Job()

        if (prefs.getBoolean("hideThumbnail", true)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        viewModel = ViewModelProviders.of(this).get(modelClass)
        nfcDispatcher = NordpolNfcDispatcher(this) {
            it.enableReaderMode(!prefs.getBoolean("disableNfcReaderMode", false)).enableUnavailableNfcUserPrompt(false)
        }
        yubiKitManager = YubiKitManager(this, null, nfcDispatcher)
        exec = yubiKitManager.handler.asCoroutineDispatcher()

        yubiKitManager.setOnYubiKeyListener(this)
        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            nfcDispatcher.interceptIntent(intent)
        }

        viewModel.needsDevice.observe(this, Observer {
            if (it) {
                yubiKitManager.triggerOnYubiKey()
            }
        })
    }

    override fun onYubiKey(transport: YubiKeyTransport?) {
        transport?.let {
            launch(exec) {
                useTransport(it)
            }
        }
    }

    open suspend fun useTransport(transport: YubiKeyTransport) {
        try {
            transport.connect().use {
                viewModel.onClient(OathClient(it, keyManager))
            }
        } catch (e: PasswordRequiredException) {
            coroutineScope {
                launch(Dispatchers.Main) {
                    supportFragmentManager.apply {
                        if (findFragmentByTag("dialog_require_password") == null) {
                            RequirePasswordDialog.newInstance(keyManager, e.deviceId, e.salt, e.isMissing).show(beginTransaction(), "dialog_require_password")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("yubioath", "Error using OathClient", e)
            val message = if (e is ApduException) {
                when (e.sw) {
                    OathApplication.SW_FILE_NOT_FOUND -> R.string.no_applet
                    OathApplication.SW_WRONG_DATA -> R.string.no_applet
                    OathApplication.SW_FILE_FULL -> R.string.storage_full
                    else -> R.string.tag_error
                }
            } else R.string.tag_error

            coroutineScope {
                launch(Dispatchers.Main) {
                    toast(message)
                }
            }
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        nfcDispatcher.interceptIntent(intent)
    }

    public override fun onPause() {
        yubiKitManager.pause()
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        yubiKitManager.resume()

        if (prefs.getBoolean("warnNfc", true) && !viewModel.nfcWarned) {
            when (val adapter = NfcAdapter.getDefaultAdapter(this)) {
                null -> R.string.no_nfc
                else -> if (!adapter.isEnabled) R.string.nfc_off else null
            }?.let {
                toast(it)
                viewModel.nfcWarned = true
            }
        }
    }
}