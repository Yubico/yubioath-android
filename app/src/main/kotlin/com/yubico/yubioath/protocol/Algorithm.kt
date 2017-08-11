package com.yubico.yubioath.protocol

import java.security.MessageDigest

/**
 * Created by Dain on 2017-04-24.
 */
sealed class Algorithm(val byteVal: Byte, name: String, val blockSize: Int) {
    val messageDigest = MessageDigest.getInstance(name)!!

    fun shortenKey(key: ByteArray) = if (key.size > blockSize) messageDigest.digest(key) else key

    override fun toString() = javaClass.simpleName

    object SHA1 : Algorithm(1, "SHA1", 64)
    object SHA256 : Algorithm(2, "SHA256", 64)
    object SHA512 : Algorithm(3, "SHA512", 128)
}