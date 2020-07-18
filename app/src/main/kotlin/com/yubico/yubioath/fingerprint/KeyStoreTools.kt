package com.yubico.yubioath.fingerprint

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeyStoreTools {
    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"

        private fun keyExists(keyName: String): Boolean {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            val aliases = keyStore.aliases()

            while (aliases.hasMoreElements()) {
                if (keyName == aliases.nextElement()) {
                    return true
                }
            }

            return false
        }

        fun getKeyFromKeyStore(keyname: String): SecretKey {
            val keyStore = createKeyStore()
            if (!keyExists(keyname)) {
                generateAesKey(keyname)
            }
            return keyStore.getKey(keyname, null) as SecretKey
        }

        private fun createKeyStore(): KeyStore {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            return keyStore
        }

        private fun generateAesKey(keyName: String) {
            try {
                val keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEY_STORE
                )
                val builder = KeyGenParameterSpec.Builder(
                        keyName,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                builder.setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setKeySize(256)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)

                builder.setUserAuthenticationRequired(true)
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()

            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException("Failed to create a symmetric key", e)
            } catch (e: NoSuchProviderException) {
                throw RuntimeException("Failed to create a symmetric key", e)
            } catch (e: InvalidAlgorithmParameterException) {
                throw RuntimeException("Failed to create a symmetric key", e)
            }

        }
    }

}
