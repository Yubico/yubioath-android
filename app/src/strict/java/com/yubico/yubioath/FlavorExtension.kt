package com.yubico.yubioath

import android.content.Context
import com.yubico.yubioath.ui.main.CredentialFragment

fun Context.isGooglePlayAvailable(): Boolean {
    return false
}

fun CredentialFragment.startQrCodeAcitivty(requestCode: Int) {}