package com.yubico.yubioath.keystore

import android.content.SharedPreferences
import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SharedPrefProvider(private val prefs: SharedPreferences) : KeyProvider {
    override fun hasKeys(deviceId: String): Boolean = prefs.contains(deviceId)

    override fun getKeys(deviceId: String): Sequence<StoredSigner> {
        return prefs.getStringSet(deviceId, null).orEmpty().asSequence().map { StringSigner(deviceId, it) }
    }

    override fun addKey(deviceId: String, secret: ByteArray) {
        val existing = prefs.getStringSet(deviceId, null).orEmpty().toMutableSet()
        existing.add(encode(secret))
        prefs.edit().putStringSet(deviceId, existing).apply()
    }

    override fun clearKeys(deviceId: String) {
        prefs.edit().remove(deviceId).apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    private inner class StringSigner(val deviceId: String, val secret: String) : StoredSigner {
        val mac: Mac = Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(decode(secret), algorithm))
        }

        override fun sign(input: ByteArray): ByteArray = mac.doFinal(input)

        override fun promote() {
            prefs.edit().putStringSet(deviceId, setOf(secret)).apply()
        }
    }

    companion object {
        private fun encode(input: ByteArray) = Base64.encodeToString(input, Base64.NO_WRAP or Base64.NO_PADDING)
        private fun decode(input: String) = Base64.decode(input, Base64.DEFAULT)
    }
}