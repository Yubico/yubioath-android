package com.yubico.yubioath.model

import android.content.SharedPreferences
import com.yubico.yubioath.BuildConfig
import com.yubico.yubioath.client.KeyManager
import com.yubico.yubioath.protocol.Algorithm
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.protocol.OathType
import com.yubico.yubioath.transport.NfcBackend
import kotlinx.coroutines.experimental.runBlocking
import nordpol.IsoCard
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(constants = BuildConfig::class, sdk = intArrayOf(21))
class YubiKeyOathTest {

    @Test
    fun testParsingKnownResponse() {
        val keyManager = KeyManager(Mockito.mock(SharedPreferences::class.java))
        val tagMock = Mockito.mock(IsoCard::class.java)
        Mockito.`when`(tagMock.transceive(Mockito.any(ByteArray::class.java))).thenReturn(
                byteArrayOf(0x79, 3, 0, 0, 0, 0x71, 0, 0x90.toByte(), 0x00), //SELECT
                byteArrayOf(0x71, 3, 'f'.toByte(), 'o'.toByte(), '0'.toByte(), 0x76, 5, 8, 0x41, 0x39, 0x7e, 0xea.toByte(), 0x90.toByte(), 0x00)) // CALCULATE_ALL
        runBlocking {
            val key = OathClient(NfcBackend(tagMock), keyManager)
            val codes = key.refreshCodes(59 / 30, mutableMapOf())
            Assert.assertEquals("94287082", codes.values.first()?.value)
        }
    }

    @Test
    fun ensureCorrectChallengeSent() {
        val keyManager = KeyManager(Mockito.mock(SharedPreferences::class.java))
        val tagMock = Mockito.mock(IsoCard::class.java)
        Mockito.`when`(tagMock.transceive(Mockito.any(ByteArray::class.java))).thenReturn(
                byteArrayOf(0x79, 3, 0, 0, 0, 0x71, 0, 0x90.toByte(), 0x00), //SELECT
                byteArrayOf(0x90.toByte(), 0x00)) // CALCULATE_ALL
        runBlocking {
            val key = OathClient(NfcBackend(tagMock), keyManager)
            Mockito.verify(tagMock).transceive(Mockito.any(ByteArray::class.java))

            key.refreshCodes(30000, mutableMapOf())
            Mockito.verify(tagMock).transceive(byteArrayOf(0x00, 0xa4.toByte(), 0x00, 0x01, 0x0a, 0x74, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
        }
    }

    @Test
    fun testStoreCodeInstruction() {
        val keyManager = KeyManager(Mockito.mock(SharedPreferences::class.java))
        val tagMock = Mockito.mock(IsoCard::class.java)
        Mockito.`when`(tagMock.transceive(Mockito.any(ByteArray::class.java))).thenReturn(
                byteArrayOf(0x79, 3, 0, 0, 0, 0x71, 0, 0x90.toByte(), 0x00), //SELECT
                byteArrayOf(0x90.toByte(), 0x00)) // PUT
        runBlocking {
            val key = OathClient(NfcBackend(tagMock), keyManager)
            Mockito.verify(tagMock).transceive(Mockito.any(ByteArray::class.java))

            key.addCredential(CredentialData(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7), "foo", OathType.TOTP, Algorithm.SHA1, 6))
            Mockito.verify(tagMock).transceive(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x11, 0x71, 0x03, 'f'.toByte(), 'o'.toByte(), 'o'.toByte(), 0x73, 0x0A, 0x21, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))

            key.addCredential(CredentialData(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7), "foo", OathType.HOTP, Algorithm.SHA256))
            Mockito.verify(tagMock).transceive(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x11, 0x71, 0x03, 'f'.toByte(), 'o'.toByte(), 'o'.toByte(), 0x73, 0x0A, 0x12, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))

            key.addCredential(CredentialData(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7), "foo", OathType.HOTP, Algorithm.SHA1, counter = 1))
            Mockito.verify(tagMock).transceive(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x17, 0x71, 0x03, 'f'.toByte(), 'o'.toByte(), 'o'.toByte(), 0x73, 0x0A, 0x11, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x7a, 0x04, 0x00, 0x00, 0x00, 0x01))
        }
    }
}