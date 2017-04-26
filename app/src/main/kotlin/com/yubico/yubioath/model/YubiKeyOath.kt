/*
 * Copyright (c) 2013, Yubico AB.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package com.yubico.yubioath.model

import android.util.Log
import com.yubico.yubioath.exc.*
import com.yubico.yubioath.transport.ApduError
import com.yubico.yubioath.transport.Backend
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.or

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/23/13
 * Time: 3:57 PM
 * To change this template use File | Settings | File Templates.
 */

class YubiKeyOath @Throws(IOException::class, AppletSelectException::class)
constructor(private val keyManager: KeyManager, private val backend: Backend) : Closeable {
    val persistent = backend.persistent

    val id: ByteArray
    val version: ByteArray
    private var challenge: ByteArray = ByteArray(0)

    init {
        try {
            val resp = send(0xa4.toByte(), p1 = 0x04) { put(AID) }

            version = resp.parseTlv(VERSION_TAG)
            checkVersion(version)

            id = resp.parseTlv(NAME_TAG)

            if (resp.hasRemaining()) {
                challenge = resp.parseTlv(CHALLENGE_TAG)
            }
        } catch (e: ApduError) {
            Log.e("yubioath", "error selecting", e)
            throw AppletMissingException()
        }
    }

    fun isLocked(): Boolean = challenge.isNotEmpty()

    @Throws(IOException::class, PasswordRequiredException::class)
    fun unlock() {
        val secrets = keyManager.getSecrets(id)

        if (secrets.isEmpty()) {
            throw PasswordRequiredException("Password is missing!", id, true)
        }

        for (secret in secrets) {
            if (doUnlock(challenge, secret)) {
                keyManager.setOnlySecret(id, secret)
                challenge = ByteArray(0)
                return
            }
        }

        throw PasswordRequiredException("Password is incorrect!", id, false)
    }

    @Throws(IOException::class)
    private fun doUnlock(challenge: ByteArray, secret: ByteArray): Boolean {
        val response = hmacSha1(secret, challenge)
        val myChallenge = ByteArray(8)
        val random = SecureRandom()
        random.nextBytes(myChallenge)
        val myResponse = hmacSha1(secret, myChallenge)

        try {
            val resp = send(VALIDATE_INS) {
                tlv(RESPONSE_TAG, response)
                tlv(CHALLENGE_TAG, myChallenge)
            }
            return Arrays.equals(myResponse, resp.parseTlv(RESPONSE_TAG))
        } catch(e: ApduError) {
            return false
        }
    }

    @Throws(IOException::class)
    private fun unsetLockCode() {
        send(SET_CODE_INS) { tlv(KEY_TAG) }
        keyManager.storeSecret(id, ByteArray(0), true)
    }

    @Throws(IOException::class)
    fun setLockCode(code: String, remember: Boolean) {
        val secret = KeyManager.calculateSecret(code, id, false)
        if (secret.isEmpty()) {
            unsetLockCode()
            return
        } else if (keyManager.getSecrets(id).contains(secret)) {
            return
        }

        val challenge = ByteArray(8)
        val random = SecureRandom()
        random.nextBytes(challenge)
        val response = hmacSha1(secret, challenge)

        send(SET_CODE_INS) {
            tlv(KEY_TAG, byteArrayOf(OathType.TOTP.byteVal or Algorithm.SHA1.byteVal) + secret)
            tlv(CHALLENGE_TAG, challenge)
            tlv(RESPONSE_TAG, response)
        }
        keyManager.setOnlySecret(id, secret)
    }

    @Throws(IOException::class)
    fun storeTotp(name: String, key: ByteArray, algorithm: Algorithm, digits: Byte) = storeCode(name, key, OathType.TOTP, algorithm, digits, 0)

    @Throws(IOException::class)
    fun storeHotp(name: String, key: ByteArray, algorithm: Algorithm, digits: Byte, imf: Int) = storeCode(name, key, OathType.HOTP, algorithm, digits, imf)

    @Throws(IOException::class)
    private fun storeCode(name: String, key: ByteArray, type: OathType, algorithm: Algorithm, digits: Byte, imf: Int) {
        try {
            send(PUT_INS) {
                tlv(NAME_TAG, name.toByteArray())
                tlv(KEY_TAG, byteArrayOf(type.byteVal or algorithm.byteVal, digits) + algorithm.shortenKey(key))
                if (type == OathType.HOTP && imf > 0) put(IMF_TAG).put(4).putInt(imf)
            }
        } catch (e: ApduError) {
            throw if (e.status == APDU_FILE_FULL) StorageFullException("No more room for OATH credentials!") else e
        }
    }

    @Throws(IOException::class)
    fun deleteCode(name: String) {
        send(DELETE_INS) { tlv(NAME_TAG, name.toByteArray()) }
    }

    @Throws(IOException::class)
    fun readCode(name: String): String {
        val steam = name.startsWith("Steam:")
        val resp = send(CALCULATE_INS, p2 = if (steam) 0 else 1) {
            tlv(NAME_TAG, name.toByteArray())
            put(CHALLENGE_TAG).put(8).putLong(System.currentTimeMillis() / 30000)
        }
        return if (steam) steamCodeFromFull(resp.parseTlv(RESPONSE_TAG)) else codeFromTruncated(resp.parseTlv(T_RESPONSE_TAG))
    }

    @Throws(IOException::class)
    fun getCodes(timestamp: Long): List<Map<String, String>> {
        val resp = send(CALCULATE_ALL_INS, p2 = 1) {
            put(CHALLENGE_TAG).put(8).putLong(timestamp)
        }

        return mutableListOf<Map<String, String>>().apply {
            while (resp.hasRemaining()) {
                val name = String(resp.parseTlv(NAME_TAG))
                val respType = resp.slice().get()  // Peek
                val hashBytes = resp.parseTlv(respType)

                if (name.startsWith("_hidden:")) continue

                add(mapOf(
                        "label" to name,
                        "code" to when (respType) {
                            T_RESPONSE_TAG -> {
                                if (name.startsWith("Steam:"))
                                    if (version[0] == 4.toByte()) {
                                        readCode(name) // We need a full response for a Steam code on YK4.
                                    } else steamCodeFromTruncated(hashBytes)
                                else
                                    codeFromTruncated(hashBytes)
                            }
                            NO_RESPONSE_TAG, TOUCH_TAG -> ""
                            else -> "<invalid code>"
                        }
                ))
            }
        }
    }

    @Throws(IOException::class)
    private fun send(ins: Byte, p1: Byte = 0, p2: Byte = 0, data: ByteBuffer.() -> Unit = {}): ByteBuffer {
        val apdu = ByteBuffer.allocate(256).put(0).put(ins).put(p1).put(p2).put(0).apply(data).let {
            it.put(4, (it.position() - 5).toByte()).array().copyOfRange(0, it.position())
        }

        return ByteBuffer.allocate(4096).apply {
            var resp = splitApduResponse(backend.sendApdu(apdu))
            while (resp.status != APDU_OK) {
                if (resp.status.shr(8).toByte() == APDU_DATA_REMAINING_SW1) {
                    put(resp.data)
                    resp = splitApduResponse(backend.sendApdu(byteArrayOf(0, SEND_REMAINING_INS, 0, 0)))
                } else {
                    throw ApduError(resp.data, resp.status)
                }
            }
            put(resp.data).limit(position()).rewind()
        }
    }

    @Throws(IOException::class)
    override fun close() = backend.close()

    companion object {
        const private val APDU_OK = 0x9000
        const private val APDU_FILE_FULL = 0x6a84
        const private val APDU_DATA_REMAINING_SW1 = 0x61.toByte()

        const private val NAME_TAG: Byte = 0x71
        const private val NAME_LIST_TAG: Byte = 0x72
        const private val KEY_TAG: Byte = 0x73
        const private val CHALLENGE_TAG: Byte = 0x74
        const private val RESPONSE_TAG: Byte = 0x75
        const private val T_RESPONSE_TAG: Byte = 0x76
        const private val NO_RESPONSE_TAG: Byte = 0x77
        const private val PROPERTY_TAG: Byte = 0x78
        const private val VERSION_TAG: Byte = 0x79
        const private val IMF_TAG: Byte = 0x7a
        const private val TOUCH_TAG: Byte = 0x7c

        const private val PUT_INS: Byte = 0x01
        const private val DELETE_INS: Byte = 0x02
        const private val SET_CODE_INS: Byte = 0x03
        const private val RESET_INS: Byte = 0x04

        const private val LIST_INS = 0xa1.toByte()
        const private val CALCULATE_INS = 0xa2.toByte()
        const private val VALIDATE_INS = 0xa3.toByte()
        const private val CALCULATE_ALL_INS = 0xa4.toByte()
        const private val SEND_REMAINING_INS = 0xa5.toByte()

        private val AID = byteArrayOf(0xa0.toByte(), 0x00, 0x00, 0x05, 0x27, 0x21, 0x01, 0x01)

        private val MOD = intArrayOf(1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000)

        @Throws(UnsupportedAppletException::class)
        private fun checkVersion(version: ByteArray) {
            // All versions currently
        }

        @Throws(IOException::class)
        private fun ByteBuffer.parseTlv(tag: Byte): ByteArray {
            val readTag = get()
            if (readTag != tag) {
                throw IOException("Required tag: %02x, got %02x".format(tag, readTag))
            }
            return ByteArray(0xff and get().toInt()).apply { get(this) }
        }

        private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
            return Mac.getInstance("HmacSHA1").apply {
                init(SecretKeySpec(key, algorithm))
            }.doFinal(data)
        }

        private fun codeFromTruncated(data: ByteArray): String {
            with(ByteBuffer.wrap(data)) {
                val num_digits = get().toInt()
                val code = int
                return String.format("%0${num_digits}d", code % MOD[num_digits])
            }
        }

        private val STEAM_CHARS = "23456789BCDFGHJKMNPQRTVWXY"

        private fun steamCodeFromTruncated(data: ByteArray): String {
            with(ByteBuffer.wrap(data)) {
                get()  //Ignore stored length for Steam
                var code = 0x7fffffff and int
                return StringBuilder().apply {
                    for (i in 0..4) {
                        append(STEAM_CHARS[code % STEAM_CHARS.length])
                        code /= STEAM_CHARS.length
                    }
                }.toString()
            }
        }

        private fun steamCodeFromFull(data: ByteArray): String {
            val offs = 0xf and data[data.size - 1].toInt() + 1
            return steamCodeFromTruncated(byteArrayOf(0) + data.copyOfRange(offs, offs + 4))
        }

        private fun ByteBuffer.tlv(tag: Byte, data: ByteArray = byteArrayOf()): ByteBuffer {
            return put(tag).put(data.size.toByte()).put(data)
        }

        private data class Response(val data: ByteArray, val status: Int)

        private fun splitApduResponse(resp: ByteArray): Response {
            return Response(
                    resp.copyOfRange(0, resp.size - 2),
                    ((0xff and resp[resp.size - 2].toInt()) shl 8) or (0xff and resp[resp.size - 1].toInt()))
        }
    }
}
