package com.yubico.yubioath.model

import android.net.Uri
import org.apache.commons.codec.binary.Base32

/**
 * Created by Dain on 2016-08-24.
 */

class CredentialData(val key: ByteArray, var name: String, val oathType: OathType, val algorithm: Algorithm = Algorithm.SHA1, val digits: Byte = 6, val counter: Int = 0) {

    companion object {
        fun from_uri(uriString: String): CredentialData {
            val uri: Uri = Uri.parse(uriString)
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

            val name = uri.path.let {
                if (it.isNullOrEmpty()) throw IllegalArgumentException("Path must contain name")
                var path = it
                if (path[0] == '/') path = path.substring(1)
                if (path.length > 64) path = path.substring(0, 64)
                path
            }

            val oathType = when (uri.host.toLowerCase()) {
                "totp" -> OathType.TOTP
                "hotp" -> OathType.HOTP
                else -> throw IllegalArgumentException("Invalid or missing OATH algorithm")
            }

            val algorithm = when (uri.getQueryParameter("algorithm").orEmpty().toLowerCase()) {
                "", "sha1" -> Algorithm.SHA1  //This is the default value
                "sha256" -> Algorithm.SHA256
                "sha512" -> Algorithm.SHA512
                else -> throw IllegalArgumentException("Invalid or missing HMAC algorithm")
            }

            val digits = with(uri.getQueryParameter("digits")) {
                if (isNullOrEmpty()) {
                    6
                } else try {
                    toByte()
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Invalid value for digits")
                }
            }

            val counter = with(uri.getQueryParameter("counter")) {
                if (isNullOrEmpty()) {
                    0
                } else try {
                    toInt()
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Invalid value for counter")
                }
            }

            return CredentialData(key, name, oathType, algorithm, digits, counter)
        }
    }
}