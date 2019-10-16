package com.yubico.yubioath.client

import android.net.Uri
import com.yubico.yubikitold.application.oath.HashAlgorithm
import com.yubico.yubikitold.application.oath.OathType
import org.apache.commons.codec.binary.Base32


data class CredentialData(val secret: ByteArray, var issuer: String?, var name: String, val oathType: OathType, val algorithm: HashAlgorithm = HashAlgorithm.SHA1, val digits: Int = 6, val period: Int = 30, val counter: Int = 0, var touch: Boolean = false) {
    companion object {
        fun fromUri(uri: Uri): CredentialData {
            val scheme = uri.scheme
            if (!uri.isHierarchical || scheme == null || scheme != "otpauth") {
                throw IllegalArgumentException("Uri scheme must be otpauth://")
            }

            val key = uri.getQueryParameter("secret").orEmpty().toUpperCase().let {
                val base32 = Base32()
                when {
                    base32.isInAlphabet(it) -> base32.decode(it)
                    else -> throw IllegalArgumentException("Secret must be base32 encoded")
                }
            }

            var data = uri.path
            if (data == null || data.isEmpty()) throw IllegalArgumentException("Path must contain name")
            if (data[0] == '/') data = data.substring(1)
            if (data.length > 64) data = data.substring(0, 64)

            val issuer = if (':' in data) {
                val parts = data.split(':', limit = 2)
                data = parts[1]
                parts[0]
            } else uri.getQueryParameter("issuer")

            val name = data

            val oathType = when (uri.host.orEmpty().toLowerCase()) {
                "totp" -> OathType.TOTP
                "hotp" -> OathType.HOTP
                else -> throw IllegalArgumentException("Invalid or missing OATH algorithm")
            }

            val algorithm = when (uri.getQueryParameter("algorithm").orEmpty().toLowerCase()) {
                "", "sha1" -> HashAlgorithm.SHA1  //This is the default value
                "sha256" -> HashAlgorithm.SHA256
                "sha512" -> HashAlgorithm.SHA512
                else -> throw IllegalArgumentException("Invalid or missing HMAC algorithm")
            }

            val digits: Int = when (uri.getQueryParameter("digits").orEmpty().trimStart('+', '0')) {
                "", "6" -> 6
                "7" -> 7
                "8" -> 8
                else -> throw IllegalArgumentException("Digits must be in range 6-8")
            }

            val period = with(uri.getQueryParameter("period")) {
                if (this == null || isEmpty()) {
                    30
                } else try {
                    toInt()
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Invalid value for period")
                }
            }

            val counter = with(uri.getQueryParameter("counter")) {
                if (this == null || isEmpty()) {
                    0
                } else try {
                    toInt()
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Invalid value for counter")
                }
            }

            return CredentialData(key, issuer, name, oathType, algorithm, digits, period, counter)
        }
    }

    val encodedSecret: String = Base32().encodeToString(secret).trimEnd('=')
}