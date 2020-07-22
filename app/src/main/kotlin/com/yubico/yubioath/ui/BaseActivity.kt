package com.yubico.yubioath.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.usb.UsbConstants
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager
import com.yubico.yubikitold.YubiKitManager
import com.yubico.yubikitold.application.ApduException
import com.yubico.yubikitold.application.oath.OathApplication
import com.yubico.yubikitold.transport.OnYubiKeyListener
import com.yubico.yubikitold.transport.YubiKeyTransport
import com.yubico.yubikitold.transport.nfc.NordpolNfcDispatcher
import com.yubico.yubioath.R
import com.yubico.yubioath.client.KeyManager
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.exc.PasswordRequiredException
import com.yubico.yubioath.fingerprint.FingerprintAuthManager
import com.yubico.yubioath.keystore.ClearingMemProvider
import com.yubico.yubioath.keystore.KeyStoreProvider
import com.yubico.yubioath.keystore.SharedPrefProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.jetbrains.anko.commit
import org.jetbrains.anko.toast
import kotlin.coroutines.CoroutineContext

abstract class BaseActivity<T : BaseViewModel>(private var modelClass: Class<T>) : AppCompatActivity(), CoroutineScope, OnYubiKeyListener {
    companion object {
        private const val SP_STORED_AUTH_KEYS = "com.yubico.yubioath.SP_STORED_AUTH_KEYS"

        private val MEM_STORE = ClearingMemProvider()

        private fun getThemeId(name: String) = when (name) {
            "Dark" -> R.style.AppThemeDark
            "AMOLED" -> R.style.AppThemeAmoled
            else -> R.style.AppThemeLight
        }
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

    private var themeId = 0

    private var devicesAuthenticatedWithFingerprint: MutableList<String> = mutableListOf<String>()

    private lateinit var fingerprintAuthManager: FingerprintAuthManager;

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
        yubiKitManager.usbDeviceManager.setUsbDeviceFilter {
            val hasCcid = 0.until(it.interfaceCount).any { i -> it.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_CSCID }
            when {
                it.vendorId != 0x1050 -> false  //Not a Yubico device, ignore it
                it.productId == 0x421 -> false
                !hasCcid -> {  //YubiKey with no CCID, display error
                    launch(Dispatchers.Main) {
                        toast(R.string.no_applet)
                    }
                    false
                }
                else -> true
            }
        }

        yubiKitManager.setOnYubiKeyListener(this)
        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            nfcDispatcher.interceptIntent(intent)
        }

        viewModel.needsDevice.observe(this, Observer {
            if (it) {
                yubiKitManager.triggerOnYubiKey()
            }
        })

        themeId = getThemeId(prefs.getString("themeSelect", "Light")!!)
        setTheme(themeId)

        fingerprintAuthManager = FingerprintAuthManager(this)
        fingerprintAuthManager.initFingerprintService()
        fingerprintAuthManager.checkFingerprint()
    }

    private fun updateTheme(newThemeId: Int) {
        if (themeId != newThemeId) {
            themeId = newThemeId
            recreate()
        }
    }

    override fun onYubiKey(transport: YubiKeyTransport?) {
        transport?.let {
            launch(exec) {
                useTransport(it)
            }
        }
    }

    open suspend fun useTransport(transport: YubiKeyTransport) {
        if (!transport.hasIso7816()) {
            Log.d("yubioath", "Device does not support ISO7816")
            coroutineScope {
                launch(Dispatchers.Main) {
                    toast(R.string.no_applet)
                }
            }
            return
        }

        try {
            transport.connect().use {
                viewModel.onClient(OathClient(it, keyManager))
            }
        } catch (e: PasswordRequiredException) {
            runAuthProcess(e.deviceId, e.salt, e.isMissing)
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
        super.onNewIntent(intent)
        nfcDispatcher.interceptIntent(intent)
    }

    public override fun onPause() {
        yubiKitManager.pause()
        clearDevicesAuthWithFingerprint()
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        yubiKitManager.resume()

        val newThemeId = getThemeId(prefs.getString("themeSelect", "Light")!!)
        if (themeId != newThemeId) {
            themeId = newThemeId
            recreate()
        }

        if (prefs.getBoolean("warnNfc", true) && !viewModel.nfcWarned) {
            when (val adapter = NfcAdapter.getDefaultAdapter(this)) {
                null -> R.string.no_nfc
                else -> if (!adapter.isEnabled) R.string.nfc_off else null
            }?.let {
                toast(it)
                viewModel.nfcWarned = true
            }
        }

        tryAuthLastDevice()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            clearDevicesAuthWithFingerprint()
        }
    }

    override fun onUserLeaveHint() {
        clearDevicesAuthWithFingerprint()

        super.onUserLeaveHint()
    }

    override fun onBackPressed() {
        fingerprintAuthManager.cancel()

        super.onBackPressed()
    }

    open suspend fun runAuthProcess(deviceId: String, salt: ByteArray, isMissing: Boolean = true) {
        coroutineScope {
            launch(Dispatchers.Main) {
                supportFragmentManager.apply {
                    if (findFragmentByTag("dialog_require_password") == null) {
                        var dialog = RequirePasswordDialog
                            .newInstance(isMissing)
                            .setOnNewPassword { password, remember, useFingerprint ->
                                keyManager.clearKeys(deviceId)
                                keyManager.addKey(deviceId, KeyManager.calculateSecret(password, salt, false), remember)
                                keyManager.addKey(deviceId, KeyManager.calculateSecret(password, salt, true), remember)
                                yubiKitManager.triggerOnYubiKey()

                                if (useFingerprint) {
                                    fingerprintAuthManager.createEncryptHandler({
                                        fingerprintAuthManager.saveAuthPassword(deviceId, password)
                                    })
                                    saveLastDevice(deviceId, salt)
                                }
                            }
                            .setOnTextFocus {
                                fingerprintAuthManager.cancel()
                            }
                            .setOnClose {
                                fingerprintAuthManager.cancel()
                            }

                        dialog.show(beginTransaction(), "dialog_require_password")

                        if (fingerprintAuthManager.hasAuthPassword(deviceId)) {
                            fingerprintAuthManager.createDecryptHandler(deviceId, {
                                val password = fingerprintAuthManager.getAuthPassword(deviceId)
                                if (password != null) {
                                    keyManager.clearKeys(deviceId)
                                    keyManager.addKey(deviceId, KeyManager.calculateSecret(password, salt, false), false)
                                    keyManager.addKey(deviceId, KeyManager.calculateSecret(password, salt, true), false)
                                    yubiKitManager.triggerOnYubiKey()

                                    devicesAuthenticatedWithFingerprint.add(deviceId)

                                    dialog.dismiss()
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    private fun clearDevicesAuthWithFingerprint() {
        if (devicesAuthenticatedWithFingerprint != null) {
            while (devicesAuthenticatedWithFingerprint.size > 0) {
                var deviceId = devicesAuthenticatedWithFingerprint.last()
                devicesAuthenticatedWithFingerprint = devicesAuthenticatedWithFingerprint.dropLast(1).toMutableList()
                keyManager.clearKeys(deviceId, true)
            }
        }
    }

    private fun tryAuthLastDevice() {
        var lastDeviceId: String? = prefs.getString("lastDeviceId", null)
        var lastDeviceSalt: String? = prefs.getString("lastDeviceSalt", null)

        if (lastDeviceId != null && lastDeviceSalt != null) {
            var lastDeviceSaltBA = Base64.decode(lastDeviceSalt, Base64.DEFAULT)

            if (fingerprintAuthManager.hasAuthPassword(lastDeviceId)) {
                fingerprintAuthManager.createDecryptHandler(lastDeviceId, {
                    val password = fingerprintAuthManager.getAuthPassword(lastDeviceId)
                    if (password != null) {
                        keyManager.clearKeys(lastDeviceId)
                        keyManager.addKey(lastDeviceId, KeyManager.calculateSecret(password, lastDeviceSaltBA, false), false)
                        keyManager.addKey(lastDeviceId, KeyManager.calculateSecret(password, lastDeviceSaltBA, true), false)
                        yubiKitManager.triggerOnYubiKey()

                        devicesAuthenticatedWithFingerprint.add(lastDeviceId)
                    }
                })
            }
        }
    }

    private fun saveLastDevice(deviceId: String, salt: ByteArray) {
        var saltEncoded = Base64.encodeToString(salt, Base64.DEFAULT)

        var editor: SharedPreferences.Editor = prefs.edit()
        prefs.commit {
            editor.putString("lastDeviceId", deviceId)
            editor.putString("lastDeviceSalt", saltEncoded)
            editor.apply()
        }
    }

}
