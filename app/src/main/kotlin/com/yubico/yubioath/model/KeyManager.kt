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

import android.content.SharedPreferences
import android.util.Base64
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/23/13
 * Time: 4:46 PM
 * To change this template use File | Settings | File Templates.
 */
class KeyManager(private val store: SharedPreferences) {
    private val memStore: MutableMap<String, MutableSet<String>> = HashMap()

    fun getSecrets(id: ByteArray): Set<ByteArray> {
        val key = KEY + bytes2string(id)
        return strings2bytes(store.getStringSet(key, getMem(key)))
    }

    private fun getMem(key: String): MutableSet<String> = memStore.getOrPut(key) { HashSet() }

    private fun doStoreSecret(id: ByteArray, secret: ByteArray, remember: Boolean) {
        val key = KEY + bytes2string(id)

        store.edit().apply {
            if (secret.isNotEmpty()) {
                val value = bytes2string(secret)
                val secrets = getMem(key)
                secrets.add(value)
                if (remember) {
                    putStringSet(key, secrets)
                } else {
                    remove(key)
                }
            } else {
                memStore.remove(key)
                remove(key)
            }
        }.apply()
    }

    fun storeSecret(id: ByteArray, secret: ByteArray, remember: Boolean) {
        doStoreSecret(id, secret, remember)
    }

    fun setOnlySecret(id: ByteArray, secret: ByteArray) {
        val remember = store.contains(KEY + bytes2string(id))
        doStoreSecret(id, ByteArray(0), true) // Clear memory
        doStoreSecret(id, secret, remember)
    }

    fun clearAll() {
        memStore.clear()
        store.edit().clear().apply()
    }

    fun clearMem() {
        memStore.clear()
    }

    companion object {
        private val KEY = "key_"

        @JvmStatic
        fun calculateSecret(password: String, id: ByteArray, legacy: Boolean): ByteArray {
            if (password.isEmpty()) {
                return ByteArray(0)
            }

            val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")

            try {
                val legacyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1And8bit")
                return doCalculateSecret(if (legacy) legacyFactory else factory, password.toCharArray(), id)
            } catch (e: NoSuchAlgorithmException) {
                // Pre 4.4, standard key factory is wrong.
                // Android < 4.4 only uses the lowest 8 bits of each character, so fix the char[].
                val pwChars = if (legacy) password.toCharArray() else password.toByteArray(Charsets.UTF_8).map(Byte::toChar).toCharArray()
                return doCalculateSecret(factory, pwChars, id)
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

        private fun bytes2string(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

        private fun string2bytes(string: String): ByteArray = Base64.decode(string, Base64.NO_WRAP)

        private fun strings2bytes(strings: Set<String>): Set<ByteArray> = strings.mapTo(HashSet()) { string2bytes(it) }
    }
}