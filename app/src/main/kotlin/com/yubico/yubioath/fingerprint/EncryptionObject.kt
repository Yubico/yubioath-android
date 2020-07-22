package com.yubico.yubioath.fingerprint

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec


class EncryptionObject {
    companion object {
        private const val KEY_NAME = "FingerprintKey"
        fun newInstance(): EncryptionObject {
            return EncryptionObject()
        }
    }

    private val key: SecretKey = KeyStoreTools.getKeyFromKeyStore(KEY_NAME)

    val cipherEnc: Cipher = createCipher()
    val cipherDec: Cipher = createCipher()

    fun encrypt(cipher: Cipher, plainText: ByteArray, separator: String): String {
        val enc = cipher.doFinal(plainText)
        return Base64.encodeToString(
            enc,
            Base64.DEFAULT
        ) + separator + Base64.encodeToString(
            cipher.iv,
            Base64.DEFAULT
        )
        return ""
    }

    fun decrypt(cipher: Cipher, encrypted: String): String {

        return cipher.doFinal(
            Base64.decode(
                encrypted,
                Base64.DEFAULT
            )
        ).toString(Charsets.UTF_8)
    }

    fun cipherForEncryption(): Cipher {
        try {
            cipherEnc.init(Cipher.ENCRYPT_MODE, key)
            return cipherEnc
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw RuntimeException("Key Permanently Invalidated", e)
        } catch (e: Exception) {
            throw RuntimeException("Failed to init Cipher", e)
        }

    }

    fun cipherForDecryption(IV: String): Cipher {
        try {
            cipherDec.init(
                Cipher.DECRYPT_MODE, key, IvParameterSpec(
                Base64.decode(
                    IV.toByteArray(Charsets.UTF_8),
                    Base64.DEFAULT
                )
            )
            )
            return cipherDec
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw RuntimeException("Key Permanently Invalidated", e)
        } catch (e: Exception) {
            throw RuntimeException("Failed to init Cipher", e)
        }

    }


    private fun createCipher(): Cipher {
        return Cipher.getInstance(
            KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7
        )
    }
}
