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

package com.yubico.yubioath.client

import android.util.Base64
import com.yubico.yubioath.keystore.KeyProvider
import com.yubico.yubioath.keystore.StoredSigner
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec


class KeyManager(private val permStore: KeyProvider, private val memStore: KeyProvider) {

    fun getKeys(deviceId: String): Sequence<StoredSigner> {
        return if(permStore.hasKeys(deviceId)) {
            permStore.getKeys(deviceId)
        } else {
            memStore.getKeys(deviceId)
        }
    }

    fun addKey(deviceId: String, secret: ByteArray, remember: Boolean) {
        if(remember) {
            memStore.clearKeys(deviceId)
            permStore.addKey(deviceId, secret)
        } else {
            permStore.clearKeys(deviceId)
            memStore.addKey(deviceId, secret)
        }
    }

    fun clearKeys(deviceId: String) {
        memStore.clearKeys(deviceId)
        permStore.clearKeys(deviceId)
    }

    fun clearAll() {
        memStore.clearAll()
        permStore.clearAll()
    }

    companion object {
        fun calculateSecret(password: String, id: ByteArray, legacy: Boolean): ByteArray {
            if (password.isEmpty()) {
                return ByteArray(0)
            }

            val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")

            return try {
                val legacyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1And8bit")
                doCalculateSecret(if (legacy) legacyFactory else factory, password.toCharArray(), id)
            } catch (e: NoSuchAlgorithmException) {
                // Pre 4.4, standard key factory is wrong.
                // Android < 4.4 only uses the lowest 8 bits of each character, so fix the char[].
                val pwChars = if (legacy) password.toCharArray() else password.toByteArray(Charsets.UTF_8).map(Byte::toChar).toCharArray()
                doCalculateSecret(factory, pwChars, id)
            }
        }

        private fun doCalculateSecret(factory: SecretKeyFactory, password: CharArray, id: ByteArray): ByteArray {
            val keyspec = PBEKeySpec(password, id, 1000, 128)
            try {
                return factory.generateSecret(keyspec).encoded
            } finally {
                keyspec.clearPassword()
            }
        }
    }
}