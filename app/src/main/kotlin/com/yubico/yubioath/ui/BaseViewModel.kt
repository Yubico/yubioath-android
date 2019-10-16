package com.yubico.yubioath.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.yubico.yubikitold.application.Version
import com.yubico.yubioath.client.DeviceInfo
import com.yubico.yubioath.client.OathClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext

abstract class BaseViewModel : ViewModel(), CoroutineScope {
    companion object {
        private val DUMMY_INFO = DeviceInfo("", false, Version(0, 0, 0), false)
        private val globalDeviceInfo = MutableLiveData<DeviceInfo>().apply { postValue(DUMMY_INFO) }
    }

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    data class ClientRequest(val deviceId: String?, val func: suspend (OathClient) -> Unit)

    private val clientRequests = Channel<ClientRequest>(Channel.UNLIMITED)

    internal var nfcWarned = false

    val deviceInfo: LiveData<DeviceInfo> = globalDeviceInfo

    protected val mutableNeedsDevice = MutableLiveData<Boolean>()
    val needsDevice: LiveData<Boolean> = mutableNeedsDevice

    override fun onCleared() {
        clientRequests.cancel()
        job.cancel()
    }

    fun <T> requestClient(id: String? = null, func: (api: OathClient) -> T): Deferred<Result<T>> = async(Dispatchers.Main) {
        Log.d("yubioath", "Requesting API...")
        val responseChannel = Channel<Result<T>>()
        clientRequests.send(ClientRequest(id) {
            responseChannel.send(runCatching { func(it) })
        })
        mutableNeedsDevice.value = true

        responseChannel.receive()
    }

    fun clearDevice() {
        globalDeviceInfo.postValue(DUMMY_INFO)
    }

    protected open suspend fun useClient(client: OathClient) = Unit
    suspend fun onClient(client: OathClient) {
        mutableNeedsDevice.postValue(false)
        try {
            globalDeviceInfo.postValue(client.deviceInfo)
            Log.d("yubioath", "Got API, checking requests...")
            while (true) {
                clientRequests.poll()?.apply {
                    if (deviceId == null || deviceId == client.deviceInfo.id) {
                        func(client)
                    }
                } ?: break
            }
            useClient(client)
        } catch (e: Exception) {
            globalDeviceInfo.postValue(DUMMY_INFO)
            throw e
        }
    }
}