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
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
    private var challenge: ByteArray = ByteArray(0)

    init {
        try {
            val resp = send(0xa4.toByte(), 0x04, 0, AID)
            var offset = 0
            val version = parseBlock(resp, offset, VERSION_TAG)
            offset += version.size + 2

            checkVersion(version)

            id = parseBlock(resp, offset, NAME_TAG)
            offset += id.size + 2
            if (resp.size - offset - 4 > 0) {
                challenge = parseBlock(resp, offset, CHALLENGE_TAG)
            }
        } catch (e:ApduError) {
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
            val resp = send(VALIDATE_INS, data= tlv(RESPONSE_TAG, response) + tlv(CHALLENGE_TAG, myChallenge))
            return Arrays.equals(myResponse, parseBlock(resp, 0, RESPONSE_TAG))
        } catch(e: ApduError) {
            return false
        }
    }

    @Throws(IOException::class)
    private fun unsetLockCode() {
        send(SET_CODE_INS, data= tlv(KEY_TAG))
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

        send(SET_CODE_INS, data=
        tlv(KEY_TAG, byteArrayOf((TOTP_TYPE.toInt() or HMAC_SHA1.toInt()).toByte()) + secret)
                + tlv(CHALLENGE_TAG, challenge)
                + tlv(RESPONSE_TAG, response))
        keyManager.setOnlySecret(id, secret)
    }

    @Throws(IOException::class)
    fun storeCode(name: String, key: ByteArray, type: Byte, digits: Int, counter: Int) {
        val algorithm = (HMAC_MASK.toInt() and type.toInt()).toByte()

        try {
            val imf = if (counter >= 0) {
                ByteBuffer.allocate(6).put(IMF_TAG).put(4).putInt(counter).array()
            } else byteArrayOf()
            send(PUT_INS, data= tlv(NAME_TAG, name.toByteArray()) + tlv(KEY_TAG, byteArrayOf(type, digits.toByte()) + hmacShortenKey(key, algorithm)  + imf))
        } catch (e:ApduError) {
            if (e.status == APDU_FILE_FULL) {
                throw StorageFullException("No more room for OATH credentials!")
            } else {
                throw e
            }
        }
    }

    private fun hmacShortenKey(key: ByteArray, algorithm: Byte):ByteArray {
        val md = MessageDigest.getInstance(when(algorithm) {
            HMAC_SHA1 -> "SHA1"
            HMAC_SHA256 -> "SHA256"
            else -> throw IllegalArgumentException("Unsupported HMAC algorithm")
        })
        val blockSize = 64 //Block size is 64 for SHA1 and SHA256
        return if (key.size > blockSize) md.digest(key) else key
    }

    @Throws(IOException::class)
    fun deleteCode(name: String) {
        send(DELETE_INS, data= tlv(NAME_TAG, name.toByteArray()))
    }

    @Throws(IOException::class)
    fun readHotpCode(name: String): String {
        val resp = send(CALCULATE_INS, p2=1, data= tlv(NAME_TAG, name.toByteArray()) + tlv(CHALLENGE_TAG))
        return codeFromTruncated(parseBlock(resp, 0, T_RESPONSE_TAG))
    }

    @Throws(IOException::class)
    fun getCodes(timestamp: Long): List<Map<String, String>> {
        val codes = ArrayList<Map<String, String>>()

        val resp = send(CALCULATE_ALL_INS, p2=1, data= tlv(CHALLENGE_TAG, ByteBuffer.allocate(8).putLong(timestamp).array()))

        var offset = 0
        while (offset < resp.size && resp[offset] == NAME_TAG) {
            val name = parseBlock(resp, offset, NAME_TAG)
            offset += name.size + 2
            val responseType = resp[offset]
            val hashBytes = parseBlock(resp, offset, responseType)
            offset += hashBytes.size + 2

            val credentialName = String(name)
            if(credentialName.startsWith("_hidden:")) continue
            val oathCode = HashMap<String, String>()

            oathCode.put("label", credentialName)
            when (responseType) {
                T_RESPONSE_TAG -> {
                    val code: String
                    if (credentialName.startsWith("Steam:")) {
                        code = steamCodeFromTruncated(hashBytes)
                    } else {
                        code = codeFromTruncated(hashBytes)
                    }
                    oathCode.put("code", code)
                }
                NO_RESPONSE_TAG -> oathCode.put("code", "")
                else -> oathCode.put("code", "<invalid code>")
            }
            codes.add(oathCode)
        }

        return codes
    }

    @Throws(IOException::class)
    private fun send(ins:Byte, p1:Byte=0, p2:Byte=0, data:ByteArray=byteArrayOf()): ByteArray {
        val buf = ByteArrayOutputStream(2048)

        var resp = splitApduResponse(backend.sendApdu(byteArrayOf(0, ins, p1, p2, data.size.toByte()) + data))

        while (resp.status != APDU_OK) {
            if(resp.status.shr(8).toByte() == APDU_DATA_REMAINING_SW1) {
                buf.write(resp.data)
                resp = splitApduResponse(backend.sendApdu(byteArrayOf(0, SEND_REMAINING_INS, 0, 0)))
            } else {
                throw ApduError(resp.data, resp.status)
            }
        }
        buf.write(resp.data)

        return buf.toByteArray()
    }

    @Throws(IOException::class)
    override fun close() {
        backend.close()
    }

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

        const private val PUT_INS: Byte = 0x01
        const private val DELETE_INS: Byte = 0x02
        const private val SET_CODE_INS: Byte = 0x03
        const private val RESET_INS: Byte = 0x04

        const private val LIST_INS = 0xa1.toByte()
        const private val CALCULATE_INS = 0xa2.toByte()
        const private val VALIDATE_INS = 0xa3.toByte()
        const private val CALCULATE_ALL_INS = 0xa4.toByte()
        const private val SEND_REMAINING_INS = 0xa5.toByte()

        const val HMAC_MASK: Byte = 0x0f
        const val HMAC_SHA1: Byte = 0x01
        const val HMAC_SHA256: Byte = 0x02

        const val OATH_MASK = 0xf0.toByte()
        const val HOTP_TYPE: Byte = 0x10
        const val TOTP_TYPE: Byte = 0x20

        private val AID = byteArrayOf(0xa0.toByte(), 0x00, 0x00, 0x05, 0x27, 0x21, 0x01, 0x01)

        private val MOD = intArrayOf(1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000)

        @Throws(UnsupportedAppletException::class)
        private fun checkVersion(version: ByteArray) {
            // All versions currently
        }

        private fun compareStatus(apdu: ByteArray, status: ByteArray): Boolean {
            return apdu[apdu.size - 2] == status[0] && apdu[apdu.size - 1] == status[1]
        }

        @Throws(IOException::class)
        private fun parseBlock(data: ByteArray, offset: Int, identifier: Byte): ByteArray {
            if (data[offset] == identifier) {
                val length = data[offset + 1].toInt()
                val block = ByteArray(length)
                System.arraycopy(data, offset + 2, block, 0, length)
                return block
            } else {
                throw IOException("Require block type: " + identifier + ", got: " + data[offset])
            }
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
                return String.format("%0" + num_digits + "d", code % MOD[num_digits])
            }
        }

        private val STEAM_CHARS = "23456789BCDFGHJKMNPQRTVWXY"

        private fun steamCodeFromTruncated(data: ByteArray): String {
            with(ByteBuffer.wrap(data)) {
                get()  //Ignore stored length for Steam
                var code = int
                return StringBuilder().apply {
                    for (i in 0..4) {
                        append(STEAM_CHARS[code % STEAM_CHARS.length])
                        code /= STEAM_CHARS.length
                    }
                }.toString()
            }
        }

        private fun tlv(tag:Byte, data:ByteArray = byteArrayOf()): ByteArray = byteArrayOf(tag, data.size.toByte()) + data

        private data class Response(val data:ByteArray, val status: Int)

        private fun splitApduResponse(resp:ByteArray): Response {
            return Response(
                    resp.copyOfRange(0, resp.size - 2),
                    ((0xff and resp[resp.size - 2].toInt()) shl 8) or (0xff and resp[resp.size - 1].toInt()))
        }
    }
}
