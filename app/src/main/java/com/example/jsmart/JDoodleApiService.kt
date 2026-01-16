package com.example.jsmart

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface JDoodleApiService {
    @Headers("Content-Type: application/json")
    @POST(BuildConfig.JDOODLE_POST_URL)
    fun executeCode(@Body request: JDoodleRequest): Call<JDoodleResponse>
}
