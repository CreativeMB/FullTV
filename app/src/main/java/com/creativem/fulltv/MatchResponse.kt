package com.creativem.fulltv

import com.google.gson.annotations.SerializedName

data class MatchResponse(
    @SerializedName("matches") val matches: List<Match>
)

data class Match(
    @SerializedName("status") val status: String,
    @SerializedName("utcDate") val utcDate: String,
    @SerializedName("stage") val stage: String,
    @SerializedName("homeTeam") val homeTeam: Team,
    @SerializedName("awayTeam") val awayTeam: Team,
    @SerializedName("score") val score: Score
)

data class Team(
    @SerializedName("name") val name: String,
    @SerializedName("crest") val crest: String? // El escudo es opcional
)

data class Score(
    @SerializedName("fullTime") val fullTime: FullTime? // Opcional por si el partido no existe
)

data class FullTime(
    @SerializedName("home") val home: Int?, // Int? es correcto porque puede ser nulo
    @SerializedName("away") val away: Int?
)