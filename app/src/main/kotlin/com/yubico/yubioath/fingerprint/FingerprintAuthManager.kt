package com.yubico.yubioath.fingerprint

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.yubico.yubioath.R
import org.jetbrains.anko.commit

class FingerprintAuthManager(private val _activity: AppCompatActivity) {
    companion object {
        private const val SECURE_KEY = "data.source.prefs.SECURE_KEY"
        private const val SEPARATOR = "-"
    }

    private val activity: AppCompatActivity = _activity

    private lateinit var fingerprintManager: FingerprintManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var cryptoObjectEncrypt: FingerprintManager.CryptoObject
    private lateinit var cryptoObjectDecrypt: FingerprintManager.CryptoObject

    private lateinit var pref: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private val encryptionObject: EncryptionObject = EncryptionObject.newInstance()

    private var currentAuthHandler: AuthHandler? = null

    fun initFingerprintService() {
        keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        fingerprintManager = activity.getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager

        pref = activity.getSharedPreferences(
            "com.yubico.secure.pref",
            Context.MODE_PRIVATE
        )
        editor = pref.edit()
    }

    fun checkFingerprint() {
        if (!keyguardManager.isKeyguardSecure) {
            Toast.makeText(activity, activity.getString(R.string.fingerpint_not_entabled_message), Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                activity,
                activity.getString(R.string.fingerpint_permissions_not_entabled_message),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (!fingerprintManager.hasEnrolledFingerprints()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.fingerpint_not_registered_message),
                Toast.LENGTH_LONG
            ).show()
            return
        }
    }

    fun createEncryptHandler(cbSuccess: () -> Unit = {}, cbFailed: () -> Unit = {}) {
        // cancel previous
        var authHandler: AuthHandler? = currentAuthHandler
        if (authHandler != null) {
            authHandler.cancel()
        }

        // create new one
        authHandler = AuthHandler(activity)
        try {
            cryptoObjectEncrypt = FingerprintManager.CryptoObject(encryptionObject.cipherForEncryption())
            authHandler.startAuth(fingerprintManager, cryptoObjectEncrypt, cbSuccess, cbFailed)
            currentAuthHandler = authHandler
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createDecryptHandler(deviceId: String, cbSuccess: () -> Unit = {}, cbFailed: () -> Unit = {}) {
        var key = SECURE_KEY + SEPARATOR + deviceId

        // cancel previous
        var authHandler: AuthHandler? = currentAuthHandler
        if (authHandler != null) {
            authHandler.cancel()
        }

        // create new one
        authHandler = AuthHandler(activity)
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

    fun cancel() {
        var authHandler: AuthHandler? = currentAuthHandler
        if (authHandler != null) {
            authHandler.cancel()
        }
    }

    fun saveAuthPassword(deviceId: String, password: String) {
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

    fun hasAuthPassword(deviceId: String): Boolean {
        var key = SECURE_KEY + SEPARATOR + deviceId
        return pref.getString(key, null) != null
    }

    fun getAuthPassword(deviceId: String): String? {
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
        } catch (e: java.lang.Exception) {
            return null
        }
    }

}
