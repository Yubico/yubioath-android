package com.yubico.yubioath.client

import android.os.Parcel
import android.os.Parcelable

data class Code(val value: String, val validFrom: Long, val validUntil: Long) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readLong(),
            parcel.readLong())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(value)
        parcel.writeLong(validFrom)
        parcel.writeLong(validUntil)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Code> {
        override fun createFromParcel(parcel: Parcel): Code {
            return Code(parcel)
        }

        override fun newArray(size: Int): Array<Code?> {
            return arrayOfNulls(size)
        }
    }
}