package com.yubico.yubioath.ui.main

import android.util.Log
import com.yubico.yubioath.client.Code
import com.yubico.yubioath.client.Credential
import com.yubico.yubioath.client.OathClient
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.protocol.OathType
import com.yubico.yubioath.ui.BaseViewModel
import com.yubico.yubioath.ui.EXEC
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import java.util.*

class OathViewModel : BaseViewModel() {
    var creds: MutableMap<Credential, Code?> = mutableMapOf()
        private set
    var searchFilter: String = ""
        set(value) {
            field = value
            credListener(creds, value)
        }
    private var refreshJob: Job? = null

    var credListener: ((Map<Credential, Code?>, String) -> Any) = { _, _ -> }
    var selectedItem: Credential? = null

    override suspend fun onStart(services: Services) {
        updateRefreshJob()
    }

    override suspend fun onStop(services: Services?) {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun addCredential(data: CredentialData) = requestClient(creds.keys.first().parentId) {
        val credential = it.addCredential(data)
        creds = it.refreshCodes(currentTime(), creds)
        credListener(creds, searchFilter)
        Log.d("yubioath", "Added credential: $credential: ${creds[credential]}")
    }

    fun calculate(credential: Credential) = requestClient(credential.parentId) {
        creds[credential] = it.calculate(credential, currentTime(true))
        credListener(creds, searchFilter)
        Log.d("yubioath", "Calculated code: $credential: ${creds[credential]}")
    }

    fun delete(credential: Credential) = requestClient(credential.parentId) {
        it.delete(credential)
        creds.remove(credential)
        credListener(creds, searchFilter)
        Log.d("yubioath", "Deleted credential: $credential")
    }

    fun clearCredentials() {
        creds.clear()
        selectedItem = null
        credListener(creds, searchFilter)
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

    override suspend fun useClient(client: OathClient) {
        creds = client.refreshCodes(currentTime(), creds)
        selectedItem?.let {
            if (!Arrays.equals(client.deviceInfo.id, it.parentId)) {
                selectedItem = null
            }
        }
        credListener(creds, searchFilter)
    }
}