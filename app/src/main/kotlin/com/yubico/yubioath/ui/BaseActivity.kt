package com.yubico.yubioath.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.hardware.usb.UsbConstants
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentManager
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
import com.yubico.yubioath.fingerprint.AuthHandler
import com.yubico.yubioath.fingerprint.EncryptionObject
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
        private const val SECURE_KEY = "data.source.prefs.SECURE_KEY"
        private const val SEPARATOR = "-"

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

        initFingerprintService()

        checkFingerprint()
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
            coroutineScope {
                launch(Dispatchers.Main) {
                    supportFragmentManager.apply {
                        if (findFragmentByTag("dialog_require_password") == null) {
                            var dialog = RequirePasswordDialog
                                .newInstance(e.isMissing)
                                .setOnNewPassword { password, remember, useFingerprint ->
                                    keyManager.clearKeys(e.deviceId)
                                    keyManager.addKey(e.deviceId, KeyManager.calculateSecret(password, e.salt, false), remember)
                                    keyManager.addKey(e.deviceId, KeyManager.calculateSecret(password, e.salt, true), remember)
                                    yubiKitManager.triggerOnYubiKey()

                                    if (useFingerprint) {
                                        createFingerprintHandlerEnc({
                                            saveAuthPassword(e.deviceId, password)
                                        })
                                    }
                                }
                                .setOnTextFocus {
                                    var authHandler: AuthHandler? = currentAuthHandler
                                    if (authHandler != null) {
                                        authHandler.cancel()
                                    }
                                }
                                .setOnClose {
                                    var authHandler: AuthHandler? = currentAuthHandler
                                    if (authHandler != null) {
                                        authHandler.cancel()
                                    }
                                }

                            dialog.show(beginTransaction(), "dialog_require_password")

                            if (hasAuthPassword(e.deviceId)) {
                                createFingerprintHandlerDec(e.deviceId, {
                                    val password = getAuthPassword(e.deviceId)
                                    if (password != null) {
                                        keyManager.clearKeys(e.deviceId)
                                        keyManager.addKey(e.deviceId, KeyManager.calculateSecret(password, e.salt, false), false)
                                        keyManager.addKey(e.deviceId, KeyManager.calculateSecret(password, e.salt, true), false)
                                        yubiKitManager.triggerOnYubiKey()

                                        devicesAuthenticatedWithFingerprint.add(e.deviceId)

                                        dialog.dismiss()
                                    }
                                })
                            }
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
        super.onNewIntent(intent)
        nfcDispatcher.interceptIntent(intent)
    }

    public override fun onPause() {
        yubiKitManager.pause()
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
    }

    public override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        // remove keys after app is moved to background - require auth again after reenter
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            if (devicesAuthenticatedWithFingerprint != null) {
                while (devicesAuthenticatedWithFingerprint.size > 0) {
                    var deviceId = devicesAuthenticatedWithFingerprint.last()
                    devicesAuthenticatedWithFingerprint = devicesAuthenticatedWithFingerprint.dropLast(1).toMutableList()
                    keyManager.clearKeys(deviceId, true)
                }
            }
        }
    }


    // fngerprint auth
    private lateinit var fingerprintManager: FingerprintManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var cryptoObjectEncrypt: FingerprintManager.CryptoObject
    private lateinit var cryptoObjectDecrypt: FingerprintManager.CryptoObject

    private lateinit var pref: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private val encryptionObject: EncryptionObject = EncryptionObject.newInstance()

    private var currentAuthHandler: AuthHandler? = null

    private fun initFingerprintService() {
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        fingerprintManager = getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager

        pref = this.getSharedPreferences(
            "com.yubico.secure.pref",
            Context.MODE_PRIVATE
        )
        editor = pref.edit()
    }

    private fun saveAuthPassword(deviceId: String, password: String) {
        var key = SECURE_KEY + SEPARATOR + deviceId

        try {
            var encryptedMessage = encryptionObject.encrypt(
                encryptionObject.cipherEnc,
                password.toByteArray(Charsets.UTF_8),
                SEPARATOR
            )
            pref.commit {
                editor.putString(key, encryptedMessage)
                editor.apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hasAuthPassword(deviceId: String) : Boolean {
        var key = SECURE_KEY + SEPARATOR + deviceId
        return pref.getString(key, null) != null
    }

    private fun getAuthPassword(deviceId: String) : String? {
        var key = SECURE_KEY + SEPARATOR + deviceId
        var mess = pref.getString(key, null)
        if (mess == null) {
            return null
        }

        try {
            mess = mess.split(SEPARATOR)[0].replace("\n", "")
            val decryptedData = encryptionObject.decrypt(
                encryptionObject.cipherDec,
                mess
            )
            return decryptedData
        }
        catch (e: java.lang.Exception) {
            return null
        }
    }

    private fun createFingerprintHandlerEnc(cbSuccess: () -> Unit = {}, cbFailed: () -> Unit = {}) {
        // cancel previous
        var authHandler: AuthHandler? = currentAuthHandler
        if (authHandler != null) {
            authHandler.cancel()
        }

        // create new one
        authHandler = AuthHandler(this)
        try {
            cryptoObjectEncrypt = FingerprintManager.CryptoObject(encryptionObject.cipherForEncryption())
            authHandler.startAuth(fingerprintManager, cryptoObjectEncrypt, cbSuccess, cbFailed)
            currentAuthHandler = authHandler
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createFingerprintHandlerDec(deviceId: String, cbSuccess: () -> Unit = {}, cbFailed: () -> Unit = {}) {
        var key = SECURE_KEY + SEPARATOR + deviceId

        // cancel previous
        var authHandler: AuthHandler? = currentAuthHandler
        if (authHandler != null) {
            authHandler.cancel()
        }

        // create new one
        authHandler = AuthHandler(this)
        try {
            var mess = pref.getString(key, null)!!.split(SEPARATOR)[1].replace("\n", "")
            cryptoObjectDecrypt = FingerprintManager.CryptoObject(
                encryptionObject.cipherForDecryption(mess)
            )
            authHandler.startAuth(fingerprintManager, cryptoObjectDecrypt, cbSuccess, cbFailed)
            currentAuthHandler = authHandler
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun checkFingerprint() {
        if (!keyguardManager.isKeyguardSecure) {
            Toast.makeText(this, getString(R.string.fingerpint_not_entabled_message), Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                this,
                getString(R.string.fingerpint_permissions_not_entabled_message),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (!fingerprintManager.hasEnrolledFingerprints()) {
            Toast.makeText(
                this,
                getString(R.string.fingerpint_not_registered_message),
                Toast.LENGTH_LONG
            ).show()
            return
        }
    }

}
