package com.creativem.fulltv
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.Response

interface FootballApiService {
    @Headers("X-Auth-Token: 9f44d0ada3c9488c8f7d0099de1a3832")
    @GET("competitions/WC/matches")
    suspend fun getWorldCupMatches(
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): Response<MatchResponse>
}