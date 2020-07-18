package com.yubico.yubioath.fingerprint

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.CancellationSignal
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.yubico.yubioath.R

class AuthHandler(private val context: Context) : FingerprintManager.AuthenticationCallback() {

    lateinit var cancellationSignal: CancellationSignal
    lateinit var callbackSuccess: () -> Unit
    lateinit var callbackFailed: () -> Unit

    private var failedTimes: Int = 0
    private var triesLimit: Int = 0

    fun startAuth(
        fingerprintManager: FingerprintManager,
        cryptoObject: FingerprintManager.CryptoObject,
        _callbackSuccess: () -> Unit,
        _callbackFailed: () -> Unit,
        _triesLimit: Int = 0
    ) {
        cancellationSignal = CancellationSignal()
        callbackSuccess = _callbackSuccess
        callbackFailed = _callbackFailed
        triesLimit = _triesLimit

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.USE_FINGERPRINT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fingerprintManager.authenticate(cryptoObject, cancellationSignal, 0, this, null)
    }

    fun cancel() {
        cancellationSignal.cancel()
    }

    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
        callbackFailed()
    }

    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
        informationMessage(context.getString(R.string.auth_success_message))
        callbackSuccess()
    }

    override fun onAuthenticationFailed() {
        ++failedTimes;

        if (triesLimit > 0) {
            if (failedTimes == triesLimit) {
                cancellationSignal.cancel()
                informationMessage(context.getString(R.string.auth_failed_message))
            }
        }
        else {
            informationMessage(context.getString(R.string.auth_failed_message))
        }
    }

    private fun informationMessage(message: String) {
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

}
