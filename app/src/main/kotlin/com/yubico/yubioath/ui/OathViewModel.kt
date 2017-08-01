package com.yubico.yubioath.ui

import android.app.PendingIntent
import android.arch.lifecycle.ViewModel
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import com.yubico.yubioath.client.Code
import com.yubico.yubioath.client.Credential
import com.yubico.yubioath.client.KeyManager
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.protocol.OathType
import com.yubico.yubioath.transport.Backend
import com.yubico.yubioath.transport.NfcBackend
import com.yubico.yubioath.transport.UsbBackend
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.sync.Mutex
import kotlinx.coroutines.experimental.sync.withLock
import nordpol.IsoCard
import org.jetbrains.anko.usbManager
import java.util.*
import java.util.concurrent.Executors

class OathViewModel : ViewModel() {
    companion object {
        const private val KEY_STORE = "NEO_STORE"
        const private val ACTION_USB_PERMISSION = "com.yubico.yubioath.USB_PERMISSION"
        private val EXEC = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    private data class Services(val context: Context, val usbManager: UsbManager, val keyManager: KeyManager)

    private val lock = Mutex()
    private var services: Services? = null
    private val clientRequests = Channel<Pair<ByteArray, (OathClient) -> Unit>>()

    var hasDevice = false
        private set
    var creds: MutableMap<Credential, Code?> = mutableMapOf()
    private var credListener: ((Map<Credential, Code?>) -> Any) = {}
    var ndefConsumed = false
    var nfcWarned = false

    fun init(context: Context) = launch(EXEC) {
        lock.withLock {
            val keyManager = KeyManager(context.getSharedPreferences(KEY_STORE, Context.MODE_PRIVATE))
            services = Services(context, context.usbManager, keyManager)
            Log.d("yubioath", "New Services!")
        }
        services?.let { checkUsb(it) }
    }

    fun setCredListener(listener: ((Map<Credential, Code?>) -> Any)) {
        credListener = listener
        Log.d("yubioath", "Credential Listener set: $listener")
    }

    fun nfcConnected(card: IsoCard) = launch(EXEC) {
        lock.withLock {
            Log.d("yubioath", "NFC DEVICE!")
            services?.let {
                useBackend(NfcBackend(card), it.keyManager)
            }
        }
    }

    fun requestClient(id: ByteArray, func: (api: OathClient) -> Unit): Job {
        Log.d("yubioath", "Requesting API...")
        val request = launch(EXEC) { clientRequests.send(Pair(id, func)) }
        Log.d("yubioath", "Request sent!")
        requestUsbCheck(0)
        return request
    }

    fun addCredential(data: CredentialData) = requestClient(creds.keys.first().parentId) {
        val credential = it.addCredential(data)
        creds = it.refreshCodes(System.currentTimeMillis(), creds)
        credListener(creds)
        Log.d("yubioath", "Added credential: $credential: ${creds[credential]}")
    }

    fun calculate(credential: Credential) = requestClient(credential.parentId) {
        creds[credential] = it.calculate(credential, System.currentTimeMillis())
        credListener(creds)
        Log.d("yubioath", "Calculated code: $credential: ${creds[credential]}")
    }

    fun delete(credential: Credential) = requestClient(credential.parentId) {
        it.delete(credential)
        creds.remove(credential)
        credListener(creds)
        Log.d("yubioath", "Deleted credential: $credential")
    }

    private fun requestUsbCheck(delayTime: Long) {
        services?.let {
            launch(EXEC) {
                delay(delayTime)
                if (services == it) {
                    checkUsb(it)
                }
            }
        }
    }

    private suspend fun checkUsb(services: Services) {
        services.usbManager.deviceList.values.find { UsbBackend.isSupported(it) }?.let {
            if (services.usbManager.hasPermission(it)) {
                Log.d("yubioath", "USB device present")
                lock.withLock {
                    useBackend(UsbBackend.connect(services.usbManager, it), services.keyManager)
                }
            } else {
                val mPermissionIntent = PendingIntent.getBroadcast(services.context, 0, Intent(ACTION_USB_PERMISSION), 0)
                services.usbManager.requestPermission(it, mPermissionIntent)
            }
        }
    }

    private suspend fun useBackend(backend: Backend, keyManager: KeyManager) {
        try {
            OathClient(backend, keyManager).use { client ->
                hasDevice = client.persistent
                Log.d("yubioath", "Got API, checking requests...")
                while (!clientRequests.isEmpty) {
                    clientRequests.receive().let { (id, func) ->
                        if (Arrays.equals(id, client.id)) {
                            func(client)
                        }
                    }
                }
                creds = client.refreshCodes(System.currentTimeMillis(), creds)
                credListener(creds)
            }
            Log.d("yubioath", "Refreshed codes: $creds")
            val deadline = creds.filterKeys { it.type == OathType.TOTP && !it.touch }.values.filterNotNull().minBy { it.validUntil }?.validUntil ?: -1
            val delayTime = deadline - System.currentTimeMillis()
            if (delayTime > 0) {
                requestUsbCheck(delayTime)
            }
        } catch(e: Exception) {
            hasDevice = false
            Log.e("yubioath", "Error using OathClient", e)
            requestUsbCheck(100)
        }
    }
}