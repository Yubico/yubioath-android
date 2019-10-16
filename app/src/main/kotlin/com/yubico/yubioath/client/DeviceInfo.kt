package com.yubico.yubioath.client

import com.yubico.yubikitold.application.Version

class DeviceInfo(val id: String, val persistent: Boolean, val version: Version, initialHasPassword: Boolean) {
    var hasPassword = initialHasPassword
        internal set
}