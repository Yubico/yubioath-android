package com.yubico.yubioath.ui

import android.app.PendingIntent
import android.arch.lifecycle.ViewModel
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.yubico.yubioath.client.Code
import com.yubico.yubioath.client.Credential
import com.yubico.yubioath.client.KeyManager
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.exc.PasswordRequiredException
import com.yubico.yubioath.fragments.RequirePasswordDialog
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.protocol.OathType
import com.yubico.yubioath.protocol.YkOathApi
import com.yubico.yubioath.transport.Backend
import com.yubico.yubioath.transport.NfcBackend
import com.yubico.yubioath.transport.UsbBackend
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
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
        const private val KEY_STORE = "NEO_STORE" //Name for legacy reasons...
        const private val ACTION_USB_PERMISSION = "com.yubico.yubioath.USB_PERMISSION"
        private val EXEC = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        private val DUMMY_INFO = YkOathApi.DeviceInfo(byteArrayOf(), false, YkOathApi.Version(0, 0, 0))
    }

    private data class Services(val context: Context, val usbManager: UsbManager, val keyManager: KeyManager)

    private val lock = Mutex()
    private var services: Services? = null
    private var usbReceiver: BroadcastReceiver? = null
    private val devicesPrompted: MutableSet<UsbDevice> = mutableSetOf()
    private val clientRequests = Channel<Pair<ByteArray, (OathClient) -> Unit>>()

    var lastDeviceInfo = DUMMY_INFO
        private set
    var creds: MutableMap<Credential, Code?> = mutableMapOf()
        private set
    var credListener: ((Map<Credential, Code?>) -> Any) = {}
    var refreshJob: Job? = null

    var selectedItem: Credential? = null
    var ndefConsumed = false
    var nfcWarned = false

    fun start(context: Context) = launch(EXEC) {
        lock.withLock {
            val keyManager = KeyManager(context.getSharedPreferences(KEY_STORE, Context.MODE_PRIVATE))
            services?.apply { usbReceiver?.let { context.unregisterReceiver(it) } }
            services = Services(context, context.usbManager, keyManager).apply {
                val filter = IntentFilter(ACTION_USB_PERMISSION)
                usbReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.d("yubioath", "USB permission granted, $device")
                            if (device != null) {
                                launch(EXEC) {
                                    lock.withLock {
                                        useBackend(UsbBackend.connect(usbManager, device), keyManager)
                                    }
                                }
                            }
                        } else {
                            Log.d("yubioath", "USB denied!")
                        }
                    }
                }
                context.registerReceiver(usbReceiver, filter)
            }
            Log.d("yubioath", "Started!")
            updateRefreshJob()
        }
    }

    fun stop() = launch(EXEC) {
        lock.withLock {
            services?.apply { context.unregisterReceiver(usbReceiver) }
            services = null
            usbReceiver = null
            refreshJob?.cancel()
            refreshJob = null
            Log.d("yubioath", "Stopped!")
        }
    }

    fun nfcConnected(card: IsoCard) = launch(EXEC) {
        lock.withLock {
            Log.d("yubioath", "NFC DEVICE!")
            services?.let {
                try {
                    useBackend(NfcBackend(card), it.keyManager)
                } catch (e: Exception) {
                    lastDeviceInfo = DUMMY_INFO
                    Log.e("yubioath", "Error using NFC device", e)
                }
            }
        }
    }

    fun requestClient(id: ByteArray, func: (api: OathClient) -> Unit) = launch(EXEC) {
        Log.d("yubioath", "Requesting API...")
        services?.let {
            launch(EXEC) { checkUsb(it) }
        }
        clientRequests.send(Pair(id, func))
    }

    fun addCredential(data: CredentialData) = requestClient(creds.keys.first().parentId) {
        val credential = it.addCredential(data)
        creds = it.refreshCodes(currentTime(), creds)
        credListener(creds)
        Log.d("yubioath", "Added credential: $credential: ${creds[credential]}")
    }

    fun calculate(credential: Credential) = requestClient(credential.parentId) {
        creds[credential] = it.calculate(credential, currentTime(true))
        credListener(creds)
        Log.d("yubioath", "Calculated code: $credential: ${creds[credential]}")
    }

    fun delete(credential: Credential) = requestClient(credential.parentId) {
        it.delete(credential)
        creds.remove(credential)
        credListener(creds)
        Log.d("yubioath", "Deleted credential: $credential")
    }

    fun clearCredentials() {
        creds.clear()
        selectedItem = null
        credListener(creds)
        updateRefreshJob()
    }

    private fun currentTime(boost: Boolean = false) = System.currentTimeMillis() + if (!boost && lastDeviceInfo.persistent) 0 else 10000

    private fun updateRefreshJob() {
        refreshJob?.cancel()
        refreshJob = launch(EXEC) {
            while (true) {
                services?.let { checkUsb(it) }

                delay(if (creds.isEmpty()) {
                    1000L
                } else {
                    val now = System.currentTimeMillis() //Needs to use real time, not adjusted for non-persistent.
                    val deadline = creds.filterKeys { it.type == OathType.TOTP && !it.touch }.values.map { it?.validUntil ?: -1 }.filter { it > now }.min()
                    if (deadline != null) deadline - now else 1000L
                })
            }
        }
    }

    private suspend fun checkUsb(services: Services) {
        val device = services.usbManager.deviceList.values.find { UsbBackend.isSupported(it) }
        if (device == null) {
            if (lastDeviceInfo.persistent) {
                Log.d("yubioath", "Persistent device removed!")
                lastDeviceInfo = DUMMY_INFO
            }
        } else {
            if (services.usbManager.hasPermission(device)) {
                Log.d("yubioath", "USB device present")
                lock.withLock {
                    useBackend(UsbBackend.connect(services.usbManager, device), services.keyManager)
                }
            } else if (device in devicesPrompted) {
                Log.d("yubioath", "USB no permission, already requested!")
            } else {
                Log.d("yubioath", "USB no permission, request")
                devicesPrompted.add(device)
                val mPermissionIntent = PendingIntent.getBroadcast(services.context, 0, Intent(ACTION_USB_PERMISSION), 0)
                services.usbManager.requestPermission(device, mPermissionIntent)
                clearCredentials()
            }
        }
    }

    private suspend fun useBackend(backend: Backend, keyManager: KeyManager) {
        try {
            OathClient(backend, keyManager).use { client ->
                lastDeviceInfo = client.deviceInfo
                Log.d("yubioath", "Got API, checking requests...")
                while (!clientRequests.isEmpty) {
                    clientRequests.receive().let { (id, func) ->
                        if (Arrays.equals(id, client.deviceInfo.id)) {
                            func(client)
                        }
                    }
                }
                creds = client.refreshCodes(currentTime(), creds)
                selectedItem?.let {
                    if (!Arrays.equals(client.deviceInfo.id, it.parentId)) {
                        selectedItem = null
                    }
                }
                credListener(creds)
            }
            Log.d("yubioath", "Refreshed codes: $creds")
        } catch (e: PasswordRequiredException) {
            launch(UI) {
                services?.apply {
                    if(context is AppCompatActivity) {
                        val fragmentManager = context.supportFragmentManager
                        val ft = fragmentManager.beginTransaction()
                        fragmentManager.findFragmentByTag("dialog")?.let { ft.remove(it) }
                        RequirePasswordDialog.newInstance(keyManager, e.id, e.isMissing).show(ft, "dialog")
                    }
                }
            }
        } catch (e: Exception) {
            lastDeviceInfo = DUMMY_INFO
            Log.e("yubioath", "Error using OathClient", e)
        }
    }
}