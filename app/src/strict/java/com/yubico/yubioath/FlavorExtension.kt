package com.yubico.yubioath

import android.content.Context
import android.content.Intent
import com.yubico.yubioath.ui.main.CredentialFragment

fun Intent.getQrCodeDisplayValue(parcelableName: String): String {
    return ""
}

fun Context.isGooglePlayAvailable(): Boolean {
    return false
}

fun CredentialFragment.startQrCodeAcitivty(requestCode: Int) {}