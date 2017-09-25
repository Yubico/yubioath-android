package com.yubico.yubioath.ui

import android.app.PendingIntent
import android.arch.lifecycle.ViewModel
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.yubico.yubioath.R
import com.yubico.yubioath.client.KeyManager
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.exc.PasswordRequiredException
import com.yubico.yubioath.keystore.ClearingMemProvider
import com.yubico.yubioath.keystore.KeyStoreProvider
import com.yubico.yubioath.keystore.SharedPrefProvider
import com.yubico.yubioath.protocol.YkOathApi
import com.yubico.yubioath.transport.Backend
import com.yubico.yubioath.transport.NfcBackend
import com.yubico.yubioath.transport.UsbBackend
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import nordpol.IsoCard
import org.jetbrains.anko.toast
import org.jetbrains.anko.usbManager
import java.util.concurrent.Executors

val EXEC = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

abstract class BaseViewModel : ViewModel() {
    companion object {
        const private val ACTION_USB_PERMISSION = "com.yubico.yubioath.USB_PERMISSION"
        const private val SP_STORED_AUTH_KEYS = "com.yubico.yubioath.SP_STORED_AUTH_KEYS"

        private val DUMMY_INFO = YkOathApi.DeviceInfo("", false, YkOathApi.Version(0, 0, 0), false)
        private val MEM_STORE = ClearingMemProvider(EXEC)
        private var sharedLastDeviceInfo = DUMMY_INFO
    }

    data class ClientResult<T>(val result:T?, val error: Throwable?)
    data class ClientRequest(val deviceId: String?, val func: suspend (OathClient) -> Unit)

    protected data class Services(val context: Context, val usbManager: UsbManager, val keyManager: KeyManager)

    protected var services: Services? = null

    private var usbReceiver: BroadcastReceiver? = null
    private val devicesPrompted: MutableSet<UsbDevice> = mutableSetOf()
    private val clientRequests = Channel<ClientRequest>()

    internal var ndefConsumed = false
    internal var nfcWarned = false

    val lastDeviceInfo: YkOathApi.DeviceInfo get() = sharedLastDeviceInfo

    protected open suspend fun onStart(services: Services) = Unit
    fun start(context: Context) = launch(EXEC) {
        val keyManager = KeyManager(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    KeyStoreProvider()
                } else {
                    SharedPrefProvider(context.getSharedPreferences(SP_STORED_AUTH_KEYS, Context.MODE_PRIVATE))
                },
                MEM_STORE
        )
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
            checkUsb(this)
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
                sharedLastDeviceInfo = DUMMY_INFO
                Log.e("yubioath", "Error using NFC device", e)
            }
        }
    }

    fun <T> requestClient(id: String? = null, func: (api: OathClient) -> T): Deferred<T> = async(EXEC) {
        Log.d("yubioath", "Requesting API...")
        services?.let {
            launch(EXEC) { checkUsb(it) }
        }

        val responseChannel = Channel<ClientResult<T>>()
        clientRequests.send(ClientRequest(id, {
            val result: ClientResult<T> = try {
                ClientResult(func(it), null)
            } catch (e: Throwable) {
                ClientResult(null, e)
            }
            responseChannel.send(result)
        }))

        responseChannel.receive().let {
            it.error?.let { throw it }
            it.result!!
        }
    }

    protected fun clearDevice() {
        sharedLastDeviceInfo = DUMMY_INFO
    }

    protected suspend fun checkUsb(services: Services) {
        val device = services.usbManager.deviceList.values.find { UsbBackend.isSupported(it) }

        when {
            device == null -> {
                if (sharedLastDeviceInfo.persistent) {
                    Log.d("yubioath", "Persistent device removed!")
                    sharedLastDeviceInfo = DUMMY_INFO
                }
            }
            services.usbManager.hasPermission(device) -> {
                Log.d("yubioath", "USB device present")
                useBackend(UsbBackend.connect(services.usbManager, device), services.keyManager)
            }
            device in devicesPrompted -> Log.d("yubioath", "USB no permission, already requested!")
            else -> {
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
                sharedLastDeviceInfo = client.deviceInfo
                Log.d("yubioath", "Got API, checking requests...")
                while (!clientRequests.isEmpty) {
                    clientRequests.receive().let { (id, func) ->
                        if (id == null || id == client.deviceInfo.id) {
                            func(client)
                        }
                    }
                }
                useClient(client)
            }
        } catch (e: PasswordRequiredException) {
            launch(UI) {
                services?.apply {
                    if (context is AppCompatActivity) {
                        context.supportFragmentManager.apply {
                            if (findFragmentByTag("dialog_require_password") == null) {
                                val transaction = beginTransaction()
                                RequirePasswordDialog.newInstance(keyManager, e.deviceId, e.salt, e.isMissing).show(transaction, "dialog_require_password")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            sharedLastDeviceInfo = DUMMY_INFO
            Log.e("yubioath", "Error using OathClient", e)
            launch(UI) {
                services?.apply {
                    context.toast(R.string.tag_error)
                }
            }
        }
    }
}