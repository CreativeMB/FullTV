package com.creativem.fulltv.mundial

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface MundialApiService {
    @Headers("X-Auth-Token: 9f44d0ada3c9488c8f7d0099de1a3832")
    @GET("competitions/WC/matches")
    suspend fun getWorldCupMatches(
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): Response<MatchResponse>
}