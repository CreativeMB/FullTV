package com.creativem.fulltv.principal

import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Movie(
    var id: String = "",
    val title: String = "",
    val correo: String = "",
    val originalTitle: String = "",
    val imageUrl: String = "",
    val streamUrl: String = "",
    // CAMBIO: Ahora es Long para que coincida con lo que enviamos desde el script
    val createdAt: Long = 0L,
    var isValid: Boolean = false,
    var isActive: Boolean = true,
    val castv: Int = 0,
    var estado: String = "activo",
    val countdownMinutes: Int = 0,
    var fechaCreacion: Long = 0L,
    val releaseDate: String = ""
) : Parcelable {

    // Constructor para Parcelable (Cómo se lee de la memoria)
    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        title = parcel.readString() ?: "",
        correo = parcel.readString() ?: "",
        originalTitle = parcel.readString() ?: "",
        imageUrl = parcel.readString() ?: "",
        streamUrl = parcel.readString() ?: "",
        createdAt = parcel.readLong(), // Leemos el Long directamente
        isValid = parcel.readByte() != 0.toByte(),
        isActive = parcel.readByte() != 0.toByte(),
        castv = parcel.readInt(),
        estado = parcel.readString() ?: "activo",
        countdownMinutes = parcel.readInt(),
        fechaCreacion = parcel.readLong(),
        releaseDate = parcel.readString() ?: ""
    )

    // Cómo se escribe en la memoria
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(title)
        parcel.writeString(correo)
        parcel.writeString(originalTitle)
        parcel.writeString(imageUrl)
        parcel.writeString(streamUrl)
        parcel.writeLong(createdAt) // Guardamos el Long
        parcel.writeByte(if (isValid) 1 else 0)
        parcel.writeByte(if (isActive) 1 else 0)
        parcel.writeInt(castv)
        parcel.writeString(estado)
        parcel.writeInt(countdownMinutes)
        parcel.writeLong(fechaCreacion)
        parcel.writeString(releaseDate)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Movie> {
        override fun createFromParcel(parcel: Parcel): Movie = Movie(parcel)
        override fun newArray(size: Int): Array<Movie?> = arrayOfNulls(size)
    }
}