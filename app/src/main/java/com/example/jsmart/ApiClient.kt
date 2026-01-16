package com.example.jsmart

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.JDOODLE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: JDoodleApiService = retrofit.create(JDoodleApiService::class.java)
}
