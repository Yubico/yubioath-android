package com.yubico.yubioath.client

import android.os.Parcel
import android.os.Parcelable
import com.yubico.yubikitold.application.oath.OathType

data class Credential(val deviceId: String, val key: String, val type: OathType?, val touch: Boolean) : Parcelable {
    val issuer: String?
    val name: String
    val period: Int

    constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readByte().let {
                if(it == 0.toByte()) null else OathType.fromValue(it)
            },
            parcel.readByte() != 0.toByte()
    )

    init {
        var data = key

        period = if (data.contains(Regex("""^\d+/"""))) {
            val parts = data.split('/', limit = 2)
            data = parts[1]
            parts[0].toInt()
        } else 30

        issuer = if (':' in data) {
            val parts = data.split(':', limit = 2)
            data = parts[1]
            parts[0]
        } else null

        name = data
    }

    override fun toString(): String = "Credential($key)"

    override fun equals(other: Any?): Boolean {
        return other is Credential && deviceId == other.deviceId && key == other.key
    }

    override fun hashCode(): Int {
        return 31 * deviceId.hashCode() + key.hashCode()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(deviceId)
        parcel.writeString(key)
        parcel.writeByte(type?.value ?: 0)
        parcel.writeByte(if (touch) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Credential> {
        override fun createFromParcel(parcel: Parcel): Credential {
            return Credential(parcel)
        }

        override fun newArray(size: Int): Array<Credential?> {
            return arrayOfNulls(size)
        }
    }
}