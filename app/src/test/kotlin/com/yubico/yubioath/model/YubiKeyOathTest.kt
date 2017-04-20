package com.yubico.yubioath.model

import android.content.SharedPreferences
import com.yubico.yubioath.BuildConfig
import com.yubico.yubioath.transport.NfcBackend
import nordpol.IsoCard
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.robolectric.RobolectricGradleTestRunner
import org.robolectric.annotation.Config

/**
 * Created by Dain on 2016-09-06.
 */
@RunWith(RobolectricGradleTestRunner::class)
@Config(constants = BuildConfig::class, sdk = intArrayOf(21))
class YubiKeyOathTest {

    @Test
    fun testParsingKnownResponse() {
        val keyManager = KeyManager(Mockito.mock(SharedPreferences::class.java))
        val tagMock = Mockito.mock(IsoCard::class.java)
        Mockito.`when`(tagMock.transceive(Mockito.any(ByteArray::class.java))).thenReturn(
                byteArrayOf(0x79, 3, 0, 0, 0, 0x71, 0, 0x90.toByte(), 0x00),  //SELECT
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
                byteArrayOf(0x79, 3, 0, 0, 0, 0x71, 0, 0x90.toByte(), 0x00),  //SELECT
                byteArrayOf(0x90.toByte(), 0x00)) // CALCULATE_ALL
        val key = YubiKeyOath(keyManager, NfcBackend(tagMock))
        Mockito.verify(tagMock).transceive(Mockito.any(ByteArray::class.java))

        key.getCodes(1)
        Mockito.verify(tagMock).transceive(byteArrayOf(0x00, 0xa4.toByte(), 0x00, 0x01, 0x0a, 0x74, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
    }
}