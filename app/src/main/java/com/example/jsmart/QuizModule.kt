package com.example.jsmart

data class QuizModule(
    val id: Int,
    val size: Int,
    val title: String,
    val questions: List<QuizQuestion>
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctAnswer: String
)
