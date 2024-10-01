package com.creativem.fulltv.ui.data

import android.os.Parcel
import android.os.Parcelable

import com.google.firebase.Timestamp

data class Movie(
    val title: String = "",
    val synopsis: String = "",
    val imageUrl: String = "",
    val streamUrl: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    var isValid: Boolean = false,
    var isActive: Boolean = true
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        Timestamp(parcel.readLong(), 0),  // Leer como long y convertir a Timestamp
        parcel.readByte() != 0.toByte(),  // Leer boolean
        parcel.readByte() != 0.toByte()   // Leer boolean
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeString(synopsis)
        parcel.writeString(imageUrl)
        parcel.writeString(streamUrl)
        parcel.writeLong(createdAt.seconds)  // Escribir Timestamp como long
        parcel.writeByte(if (isValid) 1 else 0)  // Escribir boolean como byte
        parcel.writeByte(if (isActive) 1 else 0) // Escribir boolean como byte
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Movie> {
        override fun createFromParcel(parcel: Parcel): Movie {
            return Movie(parcel)
        }

        override fun newArray(size: Int): Array<Movie?> {
            return arrayOfNulls(size)
        }
    }
}