package com.yubico.yubioath.ui.add

import android.net.Uri
import com.yubico.yubioath.client.Code
import com.yubico.yubioath.client.Credential
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.protocol.OathType
import com.yubico.yubioath.ui.BaseViewModel
import kotlinx.coroutines.Deferred

class AddCredentialViewModel : BaseViewModel() {
    private var handledUri: Uri? = null
    var data: CredentialData? = null

    fun handleScanResults(qrUri: Uri) {
        if (qrUri != handledUri) {
            handledUri = qrUri
            data = CredentialData.fromUri(qrUri)
        }
    }

    fun addCredential(credentialData: CredentialData): Deferred<Pair<Credential, Code?>> = requestClient { client ->
        client.addCredential(credentialData).let {
            Pair(it, if (!(it.touch || it.type == OathType.HOTP)) client.calculate(it, System.currentTimeMillis()) else null)
        }
    }
}