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

import com.yubico.yubioath.exc.*

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.*
import nordpol.IsoCard
import java.io.ByteArrayOutputStream
import java.io.Closeable

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/23/13
 * Time: 3:57 PM
 * To change this template use File | Settings | File Templates.
 */

class YubiKeyNeo @Throws(IOException::class, AppletSelectException::class)
constructor(private val keyManager: KeyManager, private val isoTag: IsoCard) : Closeable {
    val id: ByteArray
    private var challenge: ByteArray = ByteArray(0)

    init {
        isoTag.connect()
        isoTag.timeout = 3000
        val resp = isoTag.transceive(SELECT_COMMAND)
        if (!compareStatus(resp, APDU_OK)) {
            throw AppletMissingException()
        }

        var offset = 0
        val version = parseBlock(resp, offset, VERSION_TAG)
        offset += version.size + 2

        checkVersion(version)

        id = parseBlock(resp, offset, NAME_TAG)
        offset += id.size + 2
        if (resp.size - offset - 4 > 0) {
            challenge = parseBlock(resp, offset, CHALLENGE_TAG)
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

        val data = apdu(VALIDATE_INS) {
            tlv(RESPONSE_TAG, response)
            tlv(CHALLENGE_TAG, myChallenge)
        }

        val resp = send(data)

        if (compareStatus(resp, APDU_OK)) {
            return Arrays.equals(myResponse, parseBlock(resp, 0, RESPONSE_TAG))
        }

        return false
    }

    @Throws(IOException::class)
    private fun unsetLockCode() {
        val data = apdu(SET_CODE_INS) {
            tlv(KEY_TAG, byteArrayOf())
        }

        requireStatus(send(data), APDU_OK)
        keyManager.storeSecret(id, ByteArray(0), true)
    }

    @Throws(IOException::class)
    fun setLockCode(code: String, remember: Boolean) {
        val secret = KeyManager.calculateSecret(code, id, false)
        if (secret.size == 0) {
            unsetLockCode()
            return
        } else if (keyManager.getSecrets(id).contains(secret)) {
            return
        }

        val challenge = ByteArray(8)
        val random = SecureRandom()
        random.nextBytes(challenge)
        val response = hmacSha1(secret, challenge)

        val data = apdu(SET_CODE_INS) {
            tlv(KEY_TAG, byteArrayOf((TOTP_TYPE.toInt() or HMAC_SHA1.toInt()).toByte()) + secret)
            tlv(CHALLENGE_TAG, challenge)
            tlv(RESPONSE_TAG, response)
        }

        requireStatus(send(data), APDU_OK)
        keyManager.setOnlySecret(id, secret)
    }

    @Throws(IOException::class)
    fun storeCode(name: String, key: ByteArray, type: Byte, digits: Int, counter: Int) {
        val data = apdu(PUT_INS) {
            tlv(NAME_TAG, name.toByteArray())
            tlv(KEY_TAG, byteArrayOf(type, digits.toByte()) + if(key.size > 64) sha1(key) else key)
            if(counter >= 0) add(IMF_TAG, 4,
                    counter.ushr(24),
                    counter.ushr(16),
                    counter.ushr(8),
                    counter)
        }

        val resp = send(data)
        if (compareStatus(resp, APDU_FILE_FULL)) {
            throw StorageFullException("No more room for OATH credentials!")
        } else {
            requireStatus(resp, APDU_OK)
        }
    }

    @Throws(IOException::class)
    fun deleteCode(name: String) {
        val data = apdu(DELETE_INS) {
            tlv(NAME_TAG, name.toByteArray())
        }
        requireStatus(send(data), APDU_OK)
    }

    @Throws(IOException::class)
    fun readHotpCode(name: String): String {
        val data = apdu(CALCULATE_INS, p2=1) {
            tlv(NAME_TAG, name.toByteArray())
            tlv(CHALLENGE_TAG, byteArrayOf())
        }
        val resp = requireStatus(send(data), APDU_OK)
        return codeFromTruncated(parseBlock(resp, 0, T_RESPONSE_TAG))
    }

    @Throws(IOException::class)
    fun getCodes(timestamp: Long): List<Map<String, String>> {
        val codes = ArrayList<Map<String, String>>()

        val data = apdu(CALCULATE_ALL_INS, p2=1) {
            add(CHALLENGE_TAG, 4,
                    timestamp.shr(24),
                    timestamp.shr(16),
                    timestamp.shr(8),
                    timestamp
            )
        }
        val resp = requireStatus(send(data), APDU_OK)

        var offset = 0
        while (resp[offset] == NAME_TAG) {
            val name = parseBlock(resp, offset, NAME_TAG)
            offset += name.size + 2
            val responseType = resp[offset]
            val hashBytes = parseBlock(resp, offset, responseType)
            offset += hashBytes.size + 2

            val oathCode = HashMap<String, String>()
            val credentialName = String(name)
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
    private fun send(command: ByteArray): ByteArray {
        var resp = isoTag.transceive(command)
        val buf = ByteArrayOutputStream(2048)

        while (resp[resp.size - 2] == APDU_DATA_REMAINING_SW1) {
            buf.write(resp, 0, resp.size - 2)
            resp = isoTag.transceive(SEND_REMAINING_COMMAND)
        }

        buf.write(resp)
        return buf.toByteArray()
    }

    @Throws(IOException::class)
    override fun close() {
        isoTag.close()
    }

    companion object {
        private val APDU_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val APDU_FILE_FULL = byteArrayOf(0x6a.toByte(), 0x84.toByte())
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


        //APDU CL INS P1 P2 L ...
        //DATA 00  00 00 00 00 ...
        private val SELECT_COMMAND = byteArrayOf(0x00, 0xa4.toByte(), 0x04, 0x00, 0x08, 0xa0.toByte(), 0x00, 0x00, 0x05, 0x27, 0x21, 0x01, 0x01)
        private val SEND_REMAINING_COMMAND = byteArrayOf(0x00, SEND_REMAINING_INS, 0x00, 0x00, 0x00)

        private val MOD = intArrayOf(1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000)

        @Throws(UnsupportedAppletException::class)
        private fun checkVersion(version: ByteArray) {
            // All versions currently
        }

        private fun compareStatus(apdu: ByteArray, status: ByteArray): Boolean {
            return apdu[apdu.size - 2] == status[0] && apdu[apdu.size - 1] == status[1]
        }

        @Throws(IOException::class)
        private fun requireStatus(apdu: ByteArray, status: ByteArray): ByteArray {
            if (!compareStatus(apdu, status)) {
                val expected = "%02x%02x".format(0xff and status[0].toInt(), 0xff and status[1].toInt()).toUpperCase()
                val actual = "%02x%02x".format(0xff and apdu[apdu.size - 2].toInt(), 0xff and apdu[apdu.size - 1].toInt()).toUpperCase()
                throw IOException("Require APDU status: $expected, got $actual")
            }
            return apdu
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

        private fun sha1(data: ByteArray): ByteArray {
            try {
                val md = MessageDigest.getInstance("SHA1")
                return md.digest(data)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }

        }

        private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA1")
            val secret = SecretKeySpec(key, mac.algorithm)
            mac.init(secret)
            return mac.doFinal(data)
        }

        private fun codeFromTruncated(data: ByteArray): String {
            val intData = data.map { it.toInt() }.toIntArray()  //Bitwise operations require ints.
            val num_digits = intData[0]
            val code = (intData[1] shl 24) or ((intData[2] and 0xff) shl 16) or ((intData[3] and 0xff) shl 8) or (intData[4] and 0xff)
            return String.format("%0" + num_digits + "d", code % MOD[num_digits])
        }

        private val STEAM_CHARS = "23456789BCDFGHJKMNPQRTVWXY"

        private fun steamCodeFromTruncated(data: ByteArray): String {
            val intData = data.map { it.toInt() }.toIntArray()  //Bitwise operations require ints.
            var code = (intData[1] shl 24) or ((intData[2] and 0xff) shl 16) or ((intData[3] and 0xff) shl 8) or (intData[4] and 0xff)
            val buf = StringBuilder()
            for (i in 0..4) {
                buf.append(STEAM_CHARS[code % STEAM_CHARS.length])
                code /= STEAM_CHARS.length
            }
            return buf.toString()
        }

        private fun ByteArrayOutputStream.tlv(tag: Byte, data: ByteArray) {
            write(tag.toInt())
            write(data.size)
            write(data)
        }
        private fun ByteArrayOutputStream.add(vararg values: Number) {
            for(v in values) write(v.toInt())
        }
        private fun apdu(ins:Byte, p1: Byte = 0, p2: Byte = 0, func: ByteArrayOutputStream.() -> Unit): ByteArray {
            return ByteArrayOutputStream().apply {
                add(0, ins, p1, p2, 0)
                func()
            }.toByteArray().apply {
                set(4, (size - 5).toByte())  // Fix length field
            }
        }
    }
}
