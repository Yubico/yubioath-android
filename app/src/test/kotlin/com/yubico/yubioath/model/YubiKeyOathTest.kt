package com.yubico.yubioath.model

import com.yubico.yubikit.application.oath.HashAlgorithm
import com.yubico.yubikit.application.oath.OathType
import com.yubico.yubikit.transport.Iso7816Connection
import com.yubico.yubioath.client.CredentialData
import com.yubico.yubioath.client.KeyManager
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.keystore.KeyProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.anyByte
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk = [28])
class YubiKeyOathTest {

    @Test
    fun testParsingKnownResponse() {
        val keyManager = KeyManager(Mockito.mock(KeyProvider::class.java), Mockito.mock(KeyProvider::class.java))
        val backendMock = Mockito.mock(Iso7816Connection::class.java)
        Mockito.`when`(backendMock.send(anyByte(), anyByte(), anyByte(), anyByte(), Mockito.any(ByteArray::class.java))).thenReturn(
                byteArrayOf(0x79, 3, 0, 0, 0, 0x71, 0, 0x90.toByte(), 0x00), //SELECT
                byteArrayOf(0x71, 3, 'f'.toByte(), 'o'.toByte(), '0'.toByte(), 0x76, 5, 8, 0x41, 0x39, 0x7e, 0xea.toByte(), 0x90.toByte(), 0x00)) // CALCULATE_ALL
        runBlocking {
            val key = OathClient(backendMock, keyManager)
            val codes = key.refreshCodes(59 / 30, mutableMapOf())
            Assert.assertEquals("94287082", codes.values.first()?.value)
        }
    }

    @Test
    fun ensureCorrectChallengeSent() {
        val keyManager = KeyManager(Mockito.mock(KeyProvider::class.java), Mockito.mock(KeyProvider::class.java))
        val backendMock = Mockito.mock(Iso7816Connection::class.java)
        Mockito.`when`(backendMock.send(anyByte(), anyByte(), anyByte(), anyByte(), Mockito.any(ByteArray::class.java))).thenReturn(
                byteArrayOf(0x79, 3, 0, 0, 0, 0x71, 0, 0x90.toByte(), 0x00), //SELECT
                byteArrayOf(0x90.toByte(), 0x00)) // CALCULATE_ALL
        runBlocking {
            val key = OathClient(backendMock, keyManager)
            Mockito.verify(backendMock).send(anyByte(), anyByte(), anyByte(), anyByte(), Mockito.any(ByteArray::class.java))

            key.refreshCodes(30000, mutableMapOf())
            Mockito.verify(backendMock).send(0, 0xa4.toByte(), 0, 1, byteArrayOf(0x74, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
        }
    }

    @Test
    fun testStoreCodeInstruction() {
        val keyManager = KeyManager(Mockito.mock(KeyProvider::class.java), Mockito.mock(KeyProvider::class.java))
        val backendMock = Mockito.mock(Iso7816Connection::class.java)
        Mockito.`when`(backendMock.send(anyByte(), anyByte(), anyByte(), anyByte(), Mockito.any(ByteArray::class.java))).thenReturn(
                byteArrayOf(0x79, 3, 0, 0, 0, 0x71, 0, 0x90.toByte(), 0x00), //SELECT
                byteArrayOf(0x90.toByte(), 0x00)) // PUT
        runBlocking {
            val key = OathClient(backendMock, keyManager)
            Mockito.verify(backendMock).send(anyByte(), anyByte(), anyByte(), anyByte(), Mockito.any(ByteArray::class.java))

            key.addCredential(CredentialData(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13), null, "foo", OathType.TOTP, HashAlgorithm.SHA1, 6))
            Mockito.verify(backendMock).send(0, 1, 0, 0, byteArrayOf(0x71, 0x03, 'f'.toByte(), 'o'.toByte(), 'o'.toByte(), 0x73, 0x10, 0x21, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d))

            key.addCredential(CredentialData(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13), null, "foo", OathType.HOTP, HashAlgorithm.SHA256))
            Mockito.verify(backendMock).send(0, 1, 0, 0, byteArrayOf(0x71, 0x03, 'f'.toByte(), 'o'.toByte(), 'o'.toByte(), 0x73, 0x10, 0x12, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d))

            key.addCredential(CredentialData(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13), null, "foo", OathType.HOTP, HashAlgorithm.SHA1, counter = 1))
            Mockito.verify(backendMock).send(0, 1, 0, 0, byteArrayOf(0x71, 0x03, 'f'.toByte(), 'o'.toByte(), 'o'.toByte(), 0x73, 0x10, 0x11, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x7a, 0x04, 0x00, 0x00, 0x00, 0x01))
        }
    }
}