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
import com.yubico.yubioath.client.KeyManager
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.exc.PasswordRequiredException
import com.yubico.yubioath.fragments.RequirePasswordDialog
import com.yubico.yubioath.protocol.YkOathApi
import com.yubico.yubioath.transport.Backend
import com.yubico.yubioath.transport.NfcBackend
import com.yubico.yubioath.transport.UsbBackend
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import nordpol.IsoCard
import org.jetbrains.anko.usbManager
import java.util.*
import java.util.concurrent.Executors

/**
 * Created by Dain on 2017-08-10.
 */
val EXEC = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

abstract class BaseViewModel : ViewModel() {
    companion object {
        const private val KEY_STORE = "NEO_STORE" //Name for legacy reasons...
        const private val ACTION_USB_PERMISSION = "com.yubico.yubioath.USB_PERMISSION"
        private val DUMMY_INFO = YkOathApi.DeviceInfo(byteArrayOf(), false, YkOathApi.Version(0, 0, 0))
    }

    protected data class Services(val context: Context, val usbManager: UsbManager, val keyManager: KeyManager)

    var lastDeviceInfo = DUMMY_INFO
        private set

    protected var services: Services? = null

    private var usbReceiver: BroadcastReceiver? = null
    private val devicesPrompted: MutableSet<UsbDevice> = mutableSetOf()
    private val clientRequests = Channel<Pair<ByteArray, (OathClient) -> Unit>>()

    var ndefConsumed = false
    var nfcWarned = false

    protected open suspend fun onStart(services: Services) = Unit
    fun start(context: Context) = launch(EXEC) {
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
                                useBackend(UsbBackend.connect(usbManager, device), keyManager)
                            }
                        }
                    } else {
                        Log.d("yubioath", "USB denied!")
                    }
                }
            }
            context.registerReceiver(usbReceiver, filter)
            onStart(this)
        }
        Log.d("yubioath", "Started ViewModel: ${this@BaseViewModel}")
    }

    protected open suspend fun onStop(services: Services?) = Unit
    fun stop() = launch(EXEC) {
        services?.apply { context.unregisterReceiver(usbReceiver) }
        onStop(services)
        services = null
        usbReceiver = null
        Log.d("yubioath", "Stopped ViewModel: ${this@BaseViewModel}")
    }

    override fun onCleared() {
        services?.apply { context.unregisterReceiver(usbReceiver) }
        Log.d("yubioath", "ViewModel onCleared() called for $this")
    }

    fun nfcConnected(card: IsoCard) = launch(EXEC) {
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

    fun requestClient(id: ByteArray, func: (api: OathClient) -> Unit) = launch(EXEC) {
        Log.d("yubioath", "Requesting API...")
        services?.let {
            launch(EXEC) { checkUsb(it) }
        }
        clientRequests.send(Pair(id, func))
    }

    protected suspend fun checkUsb(services: Services) {
        val device = services.usbManager.deviceList.values.find { UsbBackend.isSupported(it) }
        if (device == null) {
            if (lastDeviceInfo.persistent) {
                Log.d("yubioath", "Persistent device removed!")
                lastDeviceInfo = DUMMY_INFO
            }
        } else {
            if (services.usbManager.hasPermission(device)) {
                Log.d("yubioath", "USB device present")
                useBackend(UsbBackend.connect(services.usbManager, device), services.keyManager)
            } else if (device in devicesPrompted) {
                Log.d("yubioath", "USB no permission, already requested!")
            } else {
                Log.d("yubioath", "USB no permission, request")
                devicesPrompted.add(device)
                val mPermissionIntent = PendingIntent.getBroadcast(services.context, 0, Intent(ACTION_USB_PERMISSION), 0)
                services.usbManager.requestPermission(device, mPermissionIntent)
            }
        }
    }

    protected open suspend fun useClient(client: OathClient) = Unit
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
                useClient(client)
            }
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