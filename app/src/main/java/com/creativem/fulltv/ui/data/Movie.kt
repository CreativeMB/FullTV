package com.creativem.fulltv.ui.data

data class Movie(
    val title: String = "",
    val synopsis: String = "",
    val imageUrl: String = "",
    val streamUrl: String = "",
    val createdAt: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val isValid: Boolean = false
)
