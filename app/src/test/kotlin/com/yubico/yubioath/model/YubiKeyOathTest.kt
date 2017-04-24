package com.yubico.yubioath.model

import android.content.SharedPreferences
import com.yubico.yubioath.BuildConfig
import com.yubico.yubioath.transport.Backend
import com.yubico.yubioath.transport.NfcBackend
import nordpol.IsoCard
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.experimental.or

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
        val key = YubiKeyOath(keyManager, NfcBackend(tagMock))
        val codes = key.getCodes(59 / 30)
        Assert.assertEquals("94287082", codes[0]["code"])
    }

    @Test
    fun ensureCorrectChallengeSent() {
        val keyManager = KeyManager(Mockito.mock(SharedPreferences::class.java))
        val tagMock = Mockito.mock(IsoCard::class.java)
        Mockito.`when`(tagMock.transceive(Mockito.any(ByteArray::class.java))).thenReturn(
                byteArrayOf(0x79, 3, 0, 0, 0, 0x71, 0, 0x90.toByte(), 0x00), //SELECT
                byteArrayOf(0x90.toByte(), 0x00)) // CALCULATE_ALL
        val key = YubiKeyOath(keyManager, NfcBackend(tagMock))
        Mockito.verify(tagMock).transceive(Mockito.any(ByteArray::class.java))

        key.getCodes(1)
        Mockito.verify(tagMock).transceive(byteArrayOf(0x00, 0xa4.toByte(), 0x00, 0x01, 0x0a, 0x74, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
    }

    @Test
    fun testStoreCodeInstruction() {
        val keyManager = KeyManager(Mockito.mock(SharedPreferences::class.java))
        val tagMock = Mockito.mock(IsoCard::class.java)
        Mockito.`when`(tagMock.transceive(Mockito.any(ByteArray::class.java))).thenReturn(
                byteArrayOf(0x79, 3, 0, 0, 0, 0x71, 0, 0x90.toByte(), 0x00), //SELECT
                byteArrayOf(0x90.toByte(), 0x00)) // PUT
        val key = YubiKeyOath(keyManager, NfcBackend(tagMock))
        Mockito.verify(tagMock).transceive(Mockito.any(ByteArray::class.java))

        key.storeTotp("foo", byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7), Algorithm.SHA1, 6)
        Mockito.verify(tagMock).transceive(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x11, 0x71, 0x03, 'f'.toByte(), 'o'.toByte(), 'o'.toByte(), 0x73, 0x0A, 0x21, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))

        key.storeHotp("foo", byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7), Algorithm.SHA256, 6, 0)
        Mockito.verify(tagMock).transceive(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x11, 0x71, 0x03, 'f'.toByte(), 'o'.toByte(), 'o'.toByte(), 0x73, 0x0A, 0x12, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))

        key.storeHotp("foo", byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7), Algorithm.SHA1, 6, 1)
        Mockito.verify(tagMock).transceive(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x17, 0x71, 0x03, 'f'.toByte(), 'o'.toByte(), 'o'.toByte(), 0x73, 0x0A, 0x11, 0x06, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x7a, 0x04, 0x00, 0x00, 0x00, 0x01))
    }
}