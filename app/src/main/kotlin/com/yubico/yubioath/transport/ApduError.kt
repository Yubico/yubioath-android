package com.yubico.yubioath.transport

import java.io.IOException

class ApduError(val data: ByteArray, val status: Int) : IOException("APDU Error: " + String.format("%04x", status)) {
    val sw1: Byte = status.shr(8).toByte()
    val sw2: Byte = status.toByte()
}