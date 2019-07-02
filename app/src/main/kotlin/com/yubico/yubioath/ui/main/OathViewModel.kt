package com.yubico.yubioath.ui.main

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.yubico.yubikit.application.oath.OathType
import com.yubico.yubioath.client.Code
import com.yubico.yubioath.client.Credential
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.ui.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OathViewModel : BaseViewModel() {
    companion object {
        const val NDEF_KEY = "NFC:NDEF"
        private const val MODHEX = "cbdefghijklnrtuv"
        val CODE_PATTERN = """(\d{6,8})|(!?[1-8$MODHEX${MODHEX.toUpperCase()}]{4}[$MODHEX]{28,60})""".toRegex()
    }

    private var credsMap : MutableMap<Credential, Code?> = mutableMapOf()
    private val _creds = MutableLiveData<Map<Credential, Code?>>()
    val creds: LiveData<Map<Credential, Code?>> = _creds

    private val _searchFilter = MutableLiveData<String>().apply { postValue("") }
    val searchFilter: LiveData<String> = _searchFilter
    fun setSearchFilter(value: String) { _searchFilter.value = value }

    private val _filteredCreds = MediatorLiveData<Map<Credential, Code?>>().apply {
        addSource(creds) { value = creds.value.orEmpty().filterKeys { it.key.contains(searchFilter.value.orEmpty(), true) } }
        addSource(searchFilter) { value = creds.value.orEmpty().filterKeys { it.key.contains(searchFilter.value.orEmpty(), true) } }
    }
    val filteredCreds : LiveData<Map<Credential, Code?>> = _filteredCreds

    private var refreshJob: Job? = null

    var selectedItem: Credential? = null

    var ndefCode: Code? = null

    fun calculate(credential: Credential) = requestClient(credential.deviceId) {
        val code = it.calculate(credential, currentTime(true))
        Log.d("yubioath", "Calculated code: $credential: $code")
        credsMap[credential] = code
        _creds.postValue(credsMap)
        scheduleRefresh()
    }

    fun delete(credential: Credential) = requestClient(credential.deviceId) {
        it.delete(credential)
        credsMap - credential
        _creds.postValue(credsMap)
        Log.d("yubioath", "Deleted credential: $credential")
        scheduleRefresh()
    }

    fun insertCredential(credential: Credential, code: Code?) {
        val deviceInfo = deviceInfo.value!!
        if (deviceInfo.id.isNotEmpty() && credential.deviceId != deviceInfo.id) throw IllegalArgumentException("Credential belongs to different device!")
        credsMap[credential] = code
        _creds.postValue(credsMap)
        scheduleRefresh()
    }

    fun clearCredentials() {
        val deviceInfo = deviceInfo.value!!
        selectedItem = null
        credsMap.clear()

        // If we have a persistent device, we try to re-read the codes and update instead of clearing.
        if (deviceInfo.persistent) {
            launch(Dispatchers.Main) {
                val refreshCreds = requestClient(deviceInfo.id) {}
                delay(100) //If we can't get the device in 100ms, give up and notify credListener.
                if (refreshCreds.isActive) {
                    _creds.postValue(mapOf())
                    scheduleRefresh()
                }
            }
        } else {
            clearDevice()
            _creds.postValue(mapOf())
            scheduleRefresh()
        }
    }

    private fun currentTime(boost: Boolean = false) = System.currentTimeMillis() + if (!boost && deviceInfo.value!!.persistent) 0 else 10000

    fun scheduleRefresh() {
        refreshJob?.cancel()
        if (creds.value.isNullOrEmpty()) {
            val info = deviceInfo.value
            if (info != null && !info.persistent) {
                mutableNeedsDevice.postValue(true)
            }
        } else {
            val now = System.currentTimeMillis() //Needs to use real time, not adjusted for non-persistent.
            val deadline = creds.value.orEmpty().filterKeys { it.type == OathType.TOTP && !it.touch }.values.map { it?.validUntil ?: -1 }.filter { it > now }.min()
            if (deadline != null) {
                Log.d("yubioath", "Refresh credentials in ${deadline - now}ms")

                refreshJob = launch(Dispatchers.Main) {
                    delay(deadline - now)
                    mutableNeedsDevice.value = true
                }

            }
        }
    }

    fun stopRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    override suspend fun useClient(client: OathClient) {
        credsMap = client.refreshCodes(currentTime(), credsMap).toMutableMap()
        ndefCode?.let {
            credsMap[Credential(client.deviceInfo.id, NDEF_KEY, null, false)] = it
        }
        ndefCode = null
        _creds.postValue(credsMap)
        selectedItem?.let {
            if (client.deviceInfo.id != it.deviceId) {
                selectedItem = null
            }
        }
        scheduleRefresh()
    }
}