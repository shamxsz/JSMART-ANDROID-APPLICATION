package com.example.jsmart

data class JDoodleResponse(
    val output: String,
    val statusCode: Int,
    val memory: String,
    val cpuTime: String
)