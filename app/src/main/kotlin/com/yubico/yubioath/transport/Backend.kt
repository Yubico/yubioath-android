package com.yubico.yubioath.transport

import java.io.Closeable

/**
 * Created by Dain on 2017-04-19.
 */
interface Backend: Closeable {
    fun sendApdu(apdu:ByteArray):ByteArray
    val persistent:Boolean
}