package com.yubico.yubioath.protocol

sealed class OathType(val byteVal: Byte) {
    override fun toString() = javaClass.simpleName.toLowerCase()

    object HOTP : OathType(0x10)
    object TOTP : OathType(0x20)

    companion object {
        fun fromValue(byteVal: Byte) = when (byteVal) {
            0x10.toByte() -> HOTP
            0x20.toByte() -> TOTP
            else -> throw IllegalArgumentException()
        }
    }
}