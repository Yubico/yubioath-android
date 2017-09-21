package com.yubico.yubioath.keystore

interface KeyProvider {
    fun hasKeys(deviceId: String): Boolean
    fun getKeys(deviceId: String): Sequence<StoredSigner>
    fun addKey(deviceId: String, secret: ByteArray)
    fun clearKeys(deviceId: String)
    fun clearAll()
}