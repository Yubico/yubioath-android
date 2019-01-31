package com.yubico.yubioath.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.yubico.yubikit.DeviceManager
import com.yubico.yubikit.application.ApduException
import com.yubico.yubikit.application.Version
import com.yubico.yubikit.application.oath.OathApplication
import com.yubico.yubikit.transport.Iso7816Backend
import com.yubico.yubikit.transport.nfc.NfcBackend
import com.yubico.yubikit.transport.usb.UsbBackend
import com.yubico.yubioath.R
import com.yubico.yubioath.client.DeviceInfo
import com.yubico.yubioath.client.KeyManager
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.exc.PasswordRequiredException
import com.yubico.yubioath.keystore.ClearingMemProvider
import com.yubico.yubioath.keystore.KeyStoreProvider
import com.yubico.yubioath.keystore.SharedPrefProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import org.jetbrains.anko.toast
import java.io.IOException
import java.util.concurrent.SynchronousQueue
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

abstract class BaseViewModel : ViewModel(), CoroutineScope {
    companion object {
        private const val SP_STORED_AUTH_KEYS = "com.yubico.yubioath.SP_STORED_AUTH_KEYS"

        val HANDLER = Handler(SynchronousQueue<Looper>().apply {
            thread(true, true, name = "Yubico Authenticator IO Handler") {
                Looper.prepare()
                put(Looper.myLooper())
                Looper.loop()
            }
        }.take())

        val EXEC = HANDLER.asCoroutineDispatcher()

        private val DUMMY_INFO = DeviceInfo("", false, Version(0, 0, 0), false)
        private val MEM_STORE = ClearingMemProvider()
        private var sharedLastDeviceInfo = DUMMY_INFO
    }

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    data class ClientRequest(val deviceId: String?, val func: suspend (OathClient) -> Unit)

    protected data class Services(val context: Context, val deviceManager: DeviceManager, val keyManager: KeyManager, val preferences: SharedPreferences)

    protected var services: Services? = null

    private val clientRequests = Channel<ClientRequest>()

    internal var ndefConsumed = false
    internal var nfcWarned = false

    val lastDeviceInfo: DeviceInfo get() = sharedLastDeviceInfo

    var ndefIntentData: ByteArray? = null

    protected open suspend fun onStart(services: Services) = Unit
    fun start(context: Context, deviceManager: DeviceManager) {
        val keyManager = KeyManager(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    KeyStoreProvider()
                } else {
                    SharedPrefProvider(context.getSharedPreferences(SP_STORED_AUTH_KEYS, Context.MODE_PRIVATE))
                },
                MEM_STORE
        )
        services = Services(context, deviceManager, keyManager, PreferenceManager.getDefaultSharedPreferences(context)).apply {
            launch(EXEC) {
                onStart(this@apply)
            }
        }
        Log.d("yubioath", "Started ViewModel: ${this}")
    }

    protected open suspend fun onStop(services: Services?) = Unit
    fun stop() = launch(EXEC) {
        onStop(services)
        services = null
        Log.d("yubioath", "Stopped ViewModel: ${this@BaseViewModel}")
    }

    override fun onCleared() {
        Log.d("yubioath", "ViewModel onCleared() called for $this")
        clientRequests.cancel()
        job.cancel()
    }

    fun <T> requestClient(id: String? = null, func: (api: OathClient) -> T): Deferred<Result<T>> = async(EXEC) {
        Log.d("yubioath", "Requesting API...")
        services?.deviceManager?.triggerOnDevice()

        val responseChannel = Channel<Result<T>>()
        clientRequests.send(ClientRequest(id) {
            responseChannel.send(runCatching {func(it)})
        })

        responseChannel.receive()
    }

    protected fun clearDevice() {
        sharedLastDeviceInfo = DUMMY_INFO
    }

    fun onBackend(backend: Iso7816Backend?) {
        services?.apply {
            launch(EXEC) {
                when (backend) {
                    is UsbBackend -> {
                        useBackend(backend, keyManager)
                    }
                    is NfcBackend -> {
                        try {
                            if (preferences.getBoolean("readNdefData", false)) {
                                ndefIntentData = backend.readRawNdefData()
                            }
                            useBackend(backend, keyManager)
                        } catch (e: IOException) {
                            sharedLastDeviceInfo = DUMMY_INFO
                            Log.e("yubioath", "Error using NFC device", e)
                        } finally {
                            ndefIntentData?.apply {
                                ndefIntentData = null
                                NfcBackend.parseNdefOtp(this)?.let { useNdefPayload(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    protected open suspend fun useClient(client: OathClient) = Unit
    private suspend fun useBackend(backend: Iso7816Backend, keyManager: KeyManager) {
        try {
            backend.connect()
            backend.use {
                val client = OathClient(it, keyManager)
                sharedLastDeviceInfo = client.deviceInfo
                Log.d("yubioath", "Got API, checking requests...")
                while (true) {
                    clientRequests.poll()?.apply {
                        if (deviceId == null || deviceId == client.deviceInfo.id) {
                            func(client)
                        }
                    } ?: break
                }
                useClient(client)
            }
        } catch (e: PasswordRequiredException) {
            launch(Dispatchers.Main) {
                services?.apply {
                    if (context is AppCompatActivity) {
                        context.supportFragmentManager.apply {
                            if (findFragmentByTag("dialog_require_password") == null) {
                                RequirePasswordDialog.newInstance(keyManager, e.deviceId, e.salt, e.isMissing).show(beginTransaction(), "dialog_require_password")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            sharedLastDeviceInfo = DUMMY_INFO
            Log.e("yubioath", "Error using OathClient", e)
            val message = if (e is ApduException) {
                when (e.sw) {
                    OathApplication.SW_FILE_NOT_FOUND -> R.string.no_applet
                    OathApplication.SW_WRONG_DATA -> R.string.no_applet
                    OathApplication.SW_FILE_FULL -> R.string.storage_full
                    else -> R.string.tag_error
                }
            } else R.string.tag_error

            launch(Dispatchers.Main) {
                services?.apply {
                    context.toast(message)
                }
            }
        }
    }

    protected open suspend fun useNdefPayload(data: ByteArray) = Unit
}