package com.creativem.fulltv.principal

import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Modelo(
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
    val voteAverage: Double = 0.0,  // Calificación (ej: 8.5)
    val overview: String = "",       // Sinopsis o descripción
    val genres: String = ""          // Géneros de la película
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
        parcel.readDouble(),
        parcel.readString() ?: "",
        parcel.readString() ?: "" // 🟢 CORREGIDO: Ahora sí lee el campo 'genres'
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
        parcel.writeDouble(voteAverage)
        parcel.writeString(overview)
        parcel.writeString(genres) // 🟢 CORREGIDO: Ahora sí escribe el campo 'genres'
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Modelo> {
        override fun createFromParcel(parcel: Parcel): Modelo = Modelo(parcel)
        override fun newArray(size: Int): Array<Modelo?> = arrayOfNulls(size)
    }

    data class AlquilerItem(
        val movie: Modelo,
        val createdAt: Long,
        val countdownMinutes: Int
    )
}