package com.yubico.yubioath.client

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.yubico.yubioath.exc.PasswordRequiredException
import com.yubico.yubioath.protocol.YkOathApi
import com.yubico.yubioath.transport.Backend
import com.yubico.yubioath.transport.NfcBackend
import com.yubico.yubioath.transport.UsbBackend
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.sync.Mutex
import nordpol.IsoCard
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Created by Dain on 2017-07-28.
 */
class BackendManager(private val context: Context, private val usbManager: UsbManager, private val keyManager: KeyManager) {
    private val ACTION_USB_PERMISSION = "com.yubico.yubioath.USB_PERMISSION"
    val EXEC = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    var usbDevice:UsbDevice? = null
    val deviceLock = Mutex()
    var credentials: MutableMap<Credential, Code?> = mutableMapOf()

    init {
        launch(EXEC) {
            while (true) {
                if(usbDevice == null) {
                    deviceLock.lock()
                    try {
                        Log.d("yubioath", "Checking for USB...")
                        pollUsb()?.let {
                            usbDevice = it
                            useBackend(UsbBackend.connect(usbManager, it))
                        } ?: delay(100)
                    } finally {
                        deviceLock.unlock()
                    }
                }
            }
        }
    }

    private fun pollUsb():UsbDevice? {
        usbManager.deviceList.values.find { UsbBackend.isSupported(it) }?.let {
            if (usbManager.hasPermission(it)) {
                return it
            } else {
                val mPermissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)
                usbManager.requestPermission(it, mPermissionIntent)
            }
        }
        return null
    }

    fun nfcConnected(card:IsoCard) = launch(EXEC) {
        deviceLock.lock()
        try {
            useBackend(NfcBackend(card))
        } finally {
            deviceLock.unlock()
        }
    }

    private suspend fun useBackend(backend: Backend) {
        val api = YkOathApi(backend)
        if (api.isLocked()) {
            val secrets = keyManager.getSecrets(api.id)

            if (secrets.isEmpty()) {
                throw PasswordRequiredException("Password is missing!", api.id, true)
            }

            secrets.find {
                api.unlock(it)
            }?.apply {
                keyManager.setOnlySecret(api.id, this)
            } ?: throw PasswordRequiredException("Password is incorrect!", api.id, false)
        }

        // Default to 30 second period
        val timestamp = System.currentTimeMillis()
        val timeStep = (timestamp / 1000 / 30)
        val challenge = ByteBuffer.allocate(8).putLong(timeStep).array()
        credentials = api.calculateAll(challenge).map {
            val credential = Credential(api.id, it.key, it.oathType, it.touch)
            val existingCode = credentials[credential]
            val code: Code? = if (it.data.size > 1) {
                if (credential.period != 30 || credential.issuer == "Steam") {
                    //Recalculate needed for for periods != 30 or Steam credentials
                    if (existingCode != null && existingCode.validUntil > timestamp) existingCode else null
                } else {
                    Code(formatTruncated(it.data), timeStep * 30 * 1000, (timeStep + 1) * 30 * 1000)
                }
            } else existingCode

            Pair(credential, code)
        }.toMap().toSortedMap(compareBy<Credential> { it.issuer }.thenBy { it.name })
        Log.d("yubioath", "CREDENTIALS\n$credentials")
    }

    companion object {
        private val STEAM_CHARS = "23456789BCDFGHJKMNPQRTVWXY"

        private fun formatTruncated(data: ByteArray): String {
            return with(ByteBuffer.wrap(data)) {
                val digits = get().toInt()
                int.toString().takeLast(digits).padStart(digits, '0')
            }
        }

        private fun formatSteam(data: ByteArray): String {
            val offs = 0xf and data[data.size - 1].toInt() + 1
            var code = 0x7fffffff and ByteBuffer.wrap(data.copyOfRange(offs, offs + 4)).int
            return StringBuilder().apply {
                for (i in 0..4) {
                    append(STEAM_CHARS[code % STEAM_CHARS.length])
                    code /= STEAM_CHARS.length
                }
            }.toString()
        }
    }
}