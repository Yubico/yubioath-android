package com.yubico.yubioath.transport

import java.io.Closeable

/**
 * Created by Dain on 2017-04-19.
 */
interface Backend: Closeable {
    @Throws(ApduError::class)
    fun sendApdu(ins:Byte, p1:Byte, p2:Byte, data:ByteArray):ByteArray

    companion object {
        const val APDU_SW_OK:Short = 0x9000.toShort()
    }
}