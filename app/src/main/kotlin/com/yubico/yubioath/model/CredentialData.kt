package com.yubico.yubioath.model

import android.net.Uri
import org.apache.commons.codec.binary.Base32

/**
 * Created by Dain on 2016-08-24.
 */

class CredentialData(uriString:String) {
    var uri: Uri = Uri.parse(uriString)
    var key:ByteArray
    var name:String
    var oath_type:Byte
    var algorithm_type:Byte
    var digits:Int
    var counter:Int

    init {
        val scheme = uri.scheme
        if (!uri.isHierarchical || scheme == null || scheme != "otpauth") {
            throw IllegalArgumentException("Uri scheme must be otpauth://")
        }

        key = uri.getQueryParameter("secret").orEmpty().toUpperCase().let {
            val base32 = Base32()
            when {
                base32.isInAlphabet(it) -> base32.decode(it)
                else -> throw IllegalArgumentException("Secret must be base32 encoded")
            }
        }

        name = uri.path.let {
            if(it.isNullOrEmpty()) throw IllegalArgumentException("Path must contain name")
            var path = it
            if(path[0] == '/') path = path.substring(1)
            if(path.length > 64) path = path.substring(0, 64)
            path
        }

        oath_type = when(uri.host.toLowerCase()) {
            "totp" -> YubiKeyNeo.TOTP_TYPE
            "hotp" -> YubiKeyNeo.HOTP_TYPE
            else -> throw IllegalArgumentException("Invalid or missing OATH algorithm")
        }

        algorithm_type = when(uri.getQueryParameter("algorithm").orEmpty().toLowerCase()) {
            "", "sha1" -> YubiKeyNeo.HMAC_SHA1  //This is the default value
            "sha256" -> YubiKeyNeo.HMAC_SHA256
            else -> throw IllegalArgumentException("Invalid or missing HMAC algorithm")
        }

        digits = with(uri.getQueryParameter("digits")) {
            if(isNullOrEmpty()) {
                6
            } else try {
                toInt()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid value for digits")
            }
        }

        counter = with(uri.getQueryParameter("counter")) {
            if(isNullOrEmpty()) {
                0
            } else try {
                toInt()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid value for counter")
            }
        }
    }
}