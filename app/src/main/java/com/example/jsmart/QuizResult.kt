package com.example.jsmart

data class QuizResult(
    val userId: String = "",
    val quizId: Int = -1,
    val score: Int = 0,
    val progress: Int = 0,
    val responses: List<Response> = emptyList()
)

data class Response(
    val question: String = "",
    val userAnswer: String = "",
    val correctAnswer: String = "",
    val timeTaken: Int = 0
)
