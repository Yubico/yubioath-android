package com.yubico.yubioath.protocol

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


class YkOathApi @Throws(IOException::class, AppletSelectException::class)
constructor(private var backend: Backend) : Closeable {
    val deviceInfo: DeviceInfo
    private var challenge: ByteArray = ByteArray(0)

    init {
        try {
            val resp = send(0xa4.toByte(), p1 = 0x04) { put(AID) }

            val version = Version.parse(resp.parseTlv(VERSION_TAG))
            checkVersion(version)

            val id = resp.parseTlv(NAME_TAG)

            deviceInfo = DeviceInfo(id, backend.persistent, version)

            if (resp.hasRemaining()) {
                challenge = resp.parseTlv(CHALLENGE_TAG)
            }
        } catch (e: ApduError) {
            throw AppletMissingException()
        }
    }

    fun isLocked(): Boolean = challenge.isNotEmpty()

    @Throws(IOException::class)
    fun unlock(secret: ByteArray): Boolean {
        val response = hmacSha1(secret, challenge)
        val myChallenge = ByteArray(8)
        val random = SecureRandom()
        random.nextBytes(myChallenge)
        val myResponse = hmacSha1(secret, myChallenge)

        return try {
            val resp = send(VALIDATE_INS) {
                tlv(RESPONSE_TAG, response)
                tlv(CHALLENGE_TAG, myChallenge)
            }
            Arrays.equals(myResponse, resp.parseTlv(RESPONSE_TAG))
        } catch(e: ApduError) {
            false
        }
    }

    @Throws(IOException::class)
    fun setLockCode(secret: ByteArray) {
        val challenge = ByteArray(8)
        val random = SecureRandom()
        random.nextBytes(challenge)
        val response = hmacSha1(secret, challenge)

        send(SET_CODE_INS) {
            tlv(KEY_TAG, byteArrayOf(OathType.TOTP.byteVal or Algorithm.SHA1.byteVal) + secret)
            tlv(CHALLENGE_TAG, challenge)
            tlv(RESPONSE_TAG, response)
        }
    }

    @Throws(IOException::class)
    fun unsetLockCode() {
        send(SET_CODE_INS) { tlv(KEY_TAG) }
    }

    @Throws(IOException::class)
    fun putCode(name: String, key: ByteArray, type: OathType, algorithm: Algorithm, digits: Byte, imf: Int, touch: Boolean) {
        if(touch && deviceInfo.version.major < 4) {
            throw IllegalArgumentException("Require touch requires YubiKey 4")
        }

        try {
            send(PUT_INS) {
                tlv(NAME_TAG, name.toByteArray())
                tlv(KEY_TAG, byteArrayOf(type.byteVal or algorithm.byteVal, digits) + algorithm.shortenKey(key))
                if (touch) put(PROPERTY_TAG).put(REQUIRE_TOUCH_PROP)
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
    fun calculate(name: String, challenge: ByteArray, truncate:Boolean = true): ByteArray {
        val resp = send(CALCULATE_INS, p2 = if (truncate) 1 else 0) {
            tlv(NAME_TAG, name.toByteArray())
            tlv(CHALLENGE_TAG, challenge)
        }
        return resp.parseTlv(resp.slice().get())
    }

    @Throws(IOException::class)
    fun calculateAll(challenge: ByteArray): List<ResponseData> {
        val resp = send(CALCULATE_ALL_INS, p2 = 1) {
            tlv(CHALLENGE_TAG, challenge)
        }

        return mutableListOf<ResponseData>().apply {
            while (resp.hasRemaining()) {
                val name = String(resp.parseTlv(NAME_TAG))
                val respType = resp.slice().get()  // Peek
                val hashBytes = resp.parseTlv(respType)
                val oathType = if (respType == NO_RESPONSE_TAG) OathType.HOTP else OathType.TOTP
                val touch = respType == TOUCH_TAG

                add(ResponseData(name, oathType, touch, hashBytes))
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
                if ((resp.status shr 8).toByte() == APDU_DATA_REMAINING_SW1) {
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
    override fun close() {
        backend.close()
        backend = object : Backend {
            override val persistent: Boolean = false
            override fun sendApdu(apdu: ByteArray): ByteArray = throw IOException("SENDING APDU ON CLOSED BACKEND!")
            override fun close() = throw IOException("Backend already closed!")
        }
    }

    data class Version(val major:Int, val minor:Int, val micro:Int) {
        companion object {
            fun parse(data: ByteArray):Version = Version(data[0].toInt(), data[1].toInt(), data[2].toInt())
        }
    }

    data class DeviceInfo(val id:ByteArray, val persistent: Boolean, val version:Version)

    class ResponseData(val key:String, val oathType: OathType, val touch:Boolean, val data:ByteArray)

    private infix fun Byte.or(b:Byte):Byte = (toInt() or b.toInt()).toByte()

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

        const private val ALWAYS_INCREASING_PROP: Byte = 0x01
        const private val REQUIRE_TOUCH_PROP: Byte = 0x02

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

        @Throws(UnsupportedAppletException::class)
        private fun checkVersion(version: Version) {
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
