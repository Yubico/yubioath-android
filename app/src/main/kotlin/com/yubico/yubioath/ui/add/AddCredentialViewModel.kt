package com.yubico.yubioath.ui.add

import android.net.Uri
import android.util.Log
import com.yubico.yubioath.protocol.CredentialData
import com.yubico.yubioath.ui.BaseViewModel

/**
 * Created by Dain on 2017-08-10.
 */
class AddCredentialViewModel : BaseViewModel() {
    private var handledUri: Uri? = null
    var data: CredentialData? = null

    fun handleScanResults(qrUri: Uri) {
        if(qrUri != handledUri) {
            handledUri = qrUri
            data = CredentialData.from_uri(qrUri)
            Log.d("yubioath", "Updated credential data from URI!")
        }
    }
}