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
    val createdAt: Long = 0L,
    var isValid: Boolean = false,
    var isActive: Boolean = true,
    val castv: Int = 0,
    var estado: String = "activo",
    val countdownMinutes: Int = 0,
    var fechaCreacion: Long = 0L,
    val releaseDate: String = "",
    // --- CAMPOS NUEVOS AGREGADOS ---
    val voteAverage: Double = 0.0,  // Calificación (ej: 8.5)
    val overview: String = "",       // Sinopsis o descripción
    val genres: String = ""
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readInt(),
        parcel.readString() ?: "activo",
        parcel.readInt(),
        parcel.readLong(),
        parcel.readString() ?: "",
        // --- LEER CAMPOS NUEVOS ---
        parcel.readDouble(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(title)
        parcel.writeString(correo)
        parcel.writeString(originalTitle)
        parcel.writeString(imageUrl)
        parcel.writeString(streamUrl)
        parcel.writeLong(createdAt)
        parcel.writeByte(if (isValid) 1 else 0)
        parcel.writeByte(if (isActive) 1 else 0)
        parcel.writeInt(castv)
        parcel.writeString(estado)
        parcel.writeInt(countdownMinutes)
        parcel.writeLong(fechaCreacion)
        parcel.writeString(releaseDate)
        // --- ESCRIBIR CAMPOS NUEVOS ---
        parcel.writeDouble(voteAverage)
        parcel.writeString(overview)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Movie> {
        override fun createFromParcel(parcel: Parcel): Movie = Movie(parcel)
        override fun newArray(size: Int): Array<Movie?> = arrayOfNulls(size)
    }
}