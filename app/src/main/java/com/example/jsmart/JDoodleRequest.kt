package com.example.jsmart

data class JDoodleRequest(
    val clientId: String,
    val clientSecret: String,
    val script: String,
    val language: String,
    val versionIndex: String
)
