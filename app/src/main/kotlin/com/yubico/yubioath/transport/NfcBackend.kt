package com.yubico.yubioath.transport

import nordpol.IsoCard

class NfcBackend(private val card: IsoCard) : Backend {
    override val persistent = false

    init {
        card.connect()
        card.timeout = 3000
    }

    override fun sendApdu(apdu: ByteArray): ByteArray = card.transceive(apdu)

    override fun close() = card.close()
}