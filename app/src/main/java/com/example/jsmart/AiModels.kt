package com.example.jsmart

data class AiAssessment(
    val grade: Int,
    val passed: Boolean,
    val summary: String,
    val strengths: List<String>,
    val improvements: List<String>,
    val recommendations: List<String>,
    val stars: String,
    val description: String,
    val nextTask: NextTask?
)

data class NextTask(
    val title: String,
    val task: String
)
