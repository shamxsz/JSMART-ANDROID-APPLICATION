package com.example.jsmart

import retrofit2.Call
import retrofit2.http.GET

interface QuotesApi {
    @GET("api/random")
    fun getRandomQuote(): Call<ProgrammingQuote>
}
