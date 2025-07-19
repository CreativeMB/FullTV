package com.creativem.fulltv.principal

import android.os.Parcel
import android.os.Parcelable

import com.google.firebase.Timestamp

data class Movie(
    val id: String = "",
    val title: String = "",
    val correo: String = "",
    val originalTitle: String = "",
    val imageUrl: String = "",
    val streamUrl: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    var isValid: Boolean = false,
    var isActive: Boolean = true,
    val castv: Int = 0,
    var estado: String = "activo",
    val countdownMinutes: Int = 0,
    var fechaCreacion: Long = 0L,
    val releaseDate: String = ""

) : Parcelable {
    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        title = parcel.readString() ?: "",
        originalTitle = parcel.readString() ?: "",
        castv = parcel.readInt(),
        imageUrl = parcel.readString() ?: "",
        streamUrl = parcel.readString() ?: "",
        createdAt = Timestamp(parcel.readLong(), parcel.readInt()), // segundos, nanosegundos
        isValid = parcel.readByte() != 0.toByte(),
        isActive = parcel.readByte() != 0.toByte(),
        countdownMinutes = parcel.readInt(),
        releaseDate = parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(title)
        parcel.writeString(originalTitle)
        parcel.writeString(imageUrl)
        parcel.writeString(streamUrl)
        parcel.writeLong(createdAt.seconds)
        parcel.writeInt(createdAt.nanoseconds)
        parcel.writeByte(if (isValid) 1 else 0)
        parcel.writeByte(if (isActive) 1 else 0)
        parcel.writeInt(castv)
        parcel.writeInt(countdownMinutes)
        parcel.writeString(releaseDate)
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