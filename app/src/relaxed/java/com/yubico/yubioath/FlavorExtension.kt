package com.yubico.yubioath

import android.content.Context
import android.content.Intent
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.yubico.yubikit.oath.qr.QrActivity
import com.yubico.yubioath.ui.main.CredentialFragment

fun Context.isGooglePlayAvailable(): Boolean {
    val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
    return (code == ConnectionResult.SUCCESS)
}

fun CredentialFragment.startQrCodeAcitivty(requestCode: Int) {
    startActivityForResult(Intent(activity, QrActivity::class.java), requestCode)
}