package com.yubico.yubioath.keystore

import android.os.Build
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RequiresApi(Build.VERSION_CODES.M)
class KeyStoreProvider : KeyProvider {
    private val keystore = KeyStore.getInstance("AndroidKeyStore")
    private val entries = hashMapOf<String, MutableSet<String>>()

    init {
        keystore.load(null)
        keystore.aliases().asSequence().map {
            it.split(',', limit = 2)
        }.forEach {
            if (it.size == 2) {
                entries.getOrPut(it[0], { mutableSetOf() }).add(it[1])
            }
        }
    }

    override fun hasKeys(deviceId: String): Boolean = !entries[deviceId].orEmpty().isEmpty()

    override fun getKeys(deviceId: String): Sequence<StoredSigner> {
        return entries[deviceId].orEmpty().sorted().asSequence().map {
            KeyStoreStoredSigner(deviceId, it)
        }
    }

    override fun addKey(deviceId: String, secret: ByteArray) {
        val keys = entries.getOrPut(deviceId, { mutableSetOf() })
        val secretId = (0..keys.size).map { "$it" }.find { !keys.contains(it) } ?: throw RuntimeException()  // Can't happen
        val alias = "$deviceId,$secretId"
        keystore.setEntry(alias, KeyStore.SecretKeyEntry(SecretKeySpec(secret, KeyProperties.KEY_ALGORITHM_HMAC_SHA1)), KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build())
        keys.add(secretId)
    }


    override fun clearKeys(deviceId: String) {
        entries.remove(deviceId).orEmpty().forEach {
            keystore.deleteEntry("$deviceId,$it")
        }
    }

    override fun clearAll() {
        keystore.aliases().asSequence().forEach { keystore.deleteEntry(it) }
        entries.clear()
    }

    private inner class KeyStoreStoredSigner(val deviceId: String, val secretId: String) : StoredSigner {
        val mac: Mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA1).apply {
            init(keystore.getKey("$deviceId,$secretId", null))
        }

        override fun sign(input: ByteArray): ByteArray = mac.doFinal(input)

        override fun promote() {
            entries[deviceId].orEmpty().filter { it != secretId }.forEach {
                keystore.deleteEntry("$deviceId,$it")
            }
            entries[deviceId] = mutableSetOf(secretId)
        }
    }
}
