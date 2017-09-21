package com.yubico.yubioath.protocol

interface ChallengeSigner {
    fun sign(input: ByteArray): ByteArray
}