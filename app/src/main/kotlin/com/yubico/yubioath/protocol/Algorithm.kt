package com.yubico.yubioath.protocol

import java.security.MessageDigest

sealed class Algorithm(val byteVal: Byte, name: String, val blockSize: Int) {
    val messageDigest = MessageDigest.getInstance(name)!!

    fun prepareKey(key: ByteArray): ByteArray = when (key.size) {
        in 0..13 -> key.copyOf(14)  // Too short, needs padding
        in 14..blockSize -> key  // No modification needed
        else -> messageDigest.digest(key)  // Too long, hash it
    }

    override fun toString(): String = javaClass.simpleName

    object SHA1 : Algorithm(1, "SHA1", 64)
    object SHA256 : Algorithm(2, "SHA256", 64)
    object SHA512 : Algorithm(3, "SHA512", 128)
}