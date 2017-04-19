package com.yubico.yubioath.transport

import nordpol.IsoCard

/**
 * Created by Dain on 2017-04-19.
 */

class NfcBackend(private val card:IsoCard): Backend {
    init {
        card.connect()
        card.timeout = 3000
    }

    override fun sendApdu(ins: Byte, p1: Byte, p2: Byte, data: ByteArray): ByteArray {
        val resp = card.transceive(byteArrayOf(0, ins, p1, p2, data.size.toByte()) + data)
        val status = ((resp[resp.size - 2].toInt() shl 8) or resp[resp.size - 1].toInt()).toShort()
        val data = resp.copyOfRange(0, resp.size - 2)

        if (status == Backend.APDU_SW_OK) {
            return data
        } else {
            throw ApduError(data, status)
        }
    }

    override fun close() {
        card.close()
    }
}