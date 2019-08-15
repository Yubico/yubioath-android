package com.yubico.yubioath

import android.content.Intent

fun Intent.getQrCodeDisplayValue(parcelableName: String): String {
    return this.getParcelableExtra<Barcode>(parcelableName).displayValue
}