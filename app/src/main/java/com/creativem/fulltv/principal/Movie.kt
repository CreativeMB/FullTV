package com.creativem.fulltv.principal

import android.os.Parcel
import android.os.Parcelable

import com.google.firebase.Timestamp

data class Movie(
    val id: String = "",  // Añadir campo id
    val title: String = "",
    val year: String = "",
    val imageUrl: String = "",
    val streamUrl: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    var isValid: Boolean = false,
    var isActive: Boolean = true,
    val casTV: String = "",
    val countdownMinutes: Int = 0
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        Timestamp(parcel.readLong(), parcel.readInt().toInt()), // Lee los segundos y nanosegundos
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readString() ?: "",
        parcel.readInt() // Leer countdownMinutes
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)  // Escribir el id
        parcel.writeString(title)
        parcel.writeString(year)
        parcel.writeString(imageUrl)
        parcel.writeString(streamUrl)
        parcel.writeLong(createdAt.seconds) // Escribir los segundos
        parcel.writeInt(createdAt.nanoseconds) // Escribir los nanosegundos
        parcel.writeByte(if (isValid) 1 else 0)
        parcel.writeByte(if (isActive) 1 else 0)
        parcel.writeString(casTV)
        parcel.writeInt(countdownMinutes) // Escribir countdownMinutes
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