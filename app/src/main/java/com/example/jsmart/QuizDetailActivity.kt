package com.example.jsmart

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jsmart.databinding.ActivityQuizDetailActivityBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson

class QuizDetailActivity : AppCompatActivity() {

    private var binding: ActivityQuizDetailActivityBinding? = null
    private lateinit var quizAdapter: QuizAdapter
    private var quizModule: QuizModule? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var firebaseUserId: String? = null
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivityQuizDetailActivityBinding.inflate(layoutInflater)
        setContentView(binding?.root)


        firebaseUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (firebaseUserId != null) {
            sharedPreferences =
                getSharedPreferences("QuizPrefs_$firebaseUserId", Context.MODE_PRIVATE)
        }

        val quizJson = intent.getStringExtra("QUIZ_DATA")
        quizModule = Gson().fromJson(quizJson, QuizModule::class.java)

        if (quizModule != null) {
            binding?.tvtitleQuiz?.text = quizModule!!.title

            quizAdapter = QuizAdapter(this, quizModule!!.id, quizModule!!.questions)
            binding?.recyclerView?.apply {
                layoutManager = LinearLayoutManager(this@QuizDetailActivity)
                adapter = quizAdapter
            }

            restoreQuizProgress(quizModule!!.id)



            val isCompleted = isQuizCompleted()
            if (isCompleted) {
                binding?.btnSubmit?.visibility = View.GONE
                binding?.btnRetake?.visibility = View.VISIBLE
                quizAdapter.showCorrectAnswers()
            } else {
                binding?.btnSubmit?.visibility = View.VISIBLE
                binding?.btnRetake?.visibility = View.GONE
                binding?.btnSubmit?.setOnClickListener {
                    checkAnswers()
                }
            }
        } else {
            Log.e("QuizDetailActivity", "Error: quizModule is null")
        }

        binding?.btnBack?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding?.btnRetake?.setOnClickListener {
            retakeQuiz(quizModule!!.id)
        }
        binding?.btnGotoNext?.setOnClickListener {
            val currentLesson = quizModule?.id ?: 0
            val nextLesson = currentLesson + 1

            val intent = Intent(this, LessonDetailActivity::class.java)
            intent.putExtra("lesson_id", nextLesson)
            startActivity(intent)
            finish()
        }
    }


    private fun checkAnswers() {
        val selectedAnswers = quizAdapter.getSelectedAnswers()
        val questionCount = quizModule?.questions?.size ?: 0

        val unanswered = mutableListOf<Int>()
        quizModule?.questions?.forEachIndexed { index, _ ->
            val userAnswer = selectedAnswers[index]
            if (userAnswer == null || userAnswer.isBlank() || userAnswer == "No Answer") {
                unanswered.add(index)
            }
        }

        if (unanswered.isNotEmpty()) {
            quizAdapter.markUnansweredQuestions(unanswered)
            Toast.makeText(this, "Please answer all questions first.", Toast.LENGTH_SHORT).show()
            return
        }


        val responsesList = mutableListOf<Response>()
        var score = 0

        quizModule?.questions?.forEachIndexed { index, question ->
            val userAnswer = selectedAnswers[index] ?: ""
            val correctAnswer = question.correctAnswer
            val isCorrect = userAnswer == correctAnswer

            val response = Response(
                question = question.question,
                userAnswer = userAnswer,
                correctAnswer = correctAnswer
            )

            responsesList.add(response)
            if (isCorrect) score++
        }

        val quizResult = QuizResult(
            userId = firebaseUserId ?: "Unknown",
            quizId = quizModule?.id ?: -1,
            progress = 1,
            score = score,
            responses = responsesList
        )

        val jsonResult = Gson().toJson(quizResult)
        Log.d("QuizResultJSON", jsonResult)

        saveQuizResult(jsonResult)
        uploadQuizResultToFirestore(quizResult)
        uploadQuizStars(score, questionCount)

        binding?.tvQuizScore?.text = "$score / $questionCount"


        quizAdapter.showCorrectAnswers()
        binding?.btnSubmit?.visibility = View.GONE
        binding?.btnRetake?.visibility = View.VISIBLE
        saveQuizProgress()

        val dialog = ScoreResultDialog(
            score = score,
            total = questionCount,
            onFinish = {}
        )
        dialog.show(supportFragmentManager, "ScoreResultDialog")
    }




    private fun saveQuizResult(jsonResult: String) {
        val editor = sharedPreferences.edit()
        editor.putString("quizResult_${quizModule?.id}", jsonResult)
        editor.apply()
    }

    private fun uploadQuizResultToFirestore(quizResult: QuizResult) {
        firebaseUserId?.let { uid ->
            val quizResultRef = firestore
                .collection("users")
                .document(uid)
                .collection("quizResults")
                .document("quiz_${quizResult.quizId}")

            quizResultRef.set(quizResult)
                .addOnSuccessListener {
                    Log.d("Firestore", "Quiz result uploaded successfully.")
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error uploading quiz result: ", e)
                }
        }
    }
    private fun restoreQuizProgress(quizId: Int) {
        val uid = firebaseUserId ?: return

        firestore.collection("users")
            .document(uid)
            .collection("quizResults")
            .document("quiz_$quizId")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Firestore", "Error listening to quiz progress:", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val quizResult = snapshot.toObject(QuizResult::class.java)
                    quizResult?.let { result ->


                        binding?.tvQuizScore?.text = "${result.score} / ${result.responses.size}"


                        quizAdapter.setUserAnswers(result.responses)


                        if (result.progress == 1) {
                            binding?.btnSubmit?.visibility = View.GONE
                            binding?.btnRetake?.visibility = View.VISIBLE
                            quizAdapter.showCorrectAnswers()
                        } else {
                            binding?.btnSubmit?.visibility = View.VISIBLE
                            binding?.btnRetake?.visibility = View.GONE
                        }
                    }
                } else {

                    binding?.tvQuizScore?.text = "0 / ${quizModule?.questions?.size ?: 0}"
                    binding?.btnSubmit?.visibility = View.VISIBLE
                    binding?.btnRetake?.visibility = View.GONE
                }
            }
    }




    private fun uploadQuizStars(score: Int, total: Int) {
        val userId = firebaseUserId ?: return
        val quizId = quizModule?.id ?: return


        val starCount = when {
            score == total -> 5
            score >= total * 0.8 -> 4
            score >= total * 0.6 -> 3
            score >= total * 0.4 -> 2
            score > 0 -> 1
            else -> 0
        }

        val quizStarsRef = firestore
            .collection("users")
            .document(userId)
            .collection("starCollection")
            .document("quizStars")


        val updateData = mapOf(
            "quiz_$quizId" to mapOf("stars" to starCount)
        )

        quizStarsRef.set(updateData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Firestore", "Quiz stars uploaded successfully for quiz $quizId. Stars: $starCount")

            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error uploading quiz stars for quiz $quizId", e)
            }
    }


    private fun saveQuizProgress() {
        firebaseUserId?.let {
            sharedPreferences.edit()
                .putBoolean("quizCompleted_${quizModule?.id}", true)
                .apply()
        }
    }

    private fun isQuizCompleted(): Boolean {
        return firebaseUserId?.let {
            sharedPreferences.getBoolean("quizCompleted_${quizModule?.id}", false)
        } ?: false
    }



    private fun retakeQuiz(quizId: Int) {
        val uid = firebaseUserId ?: return
        val firestore = FirebaseFirestore.getInstance()

        val quizStarsRef = firestore
            .collection("users")
            .document(uid)
            .collection("starCollection")
            .document("quizStars")

        val exerciseStarsRef = firestore
            .collection("users")
            .document(uid)
            .collection("starCollection")
            .document("exerciseStars")

        val totalStarsRef = firestore
            .collection("users")
            .document(uid)
            .collection("starCollection")
            .document("totalStars")

        quizStarsRef.get().addOnSuccessListener { quizDoc ->
            val currentStars = (quizDoc.getLong("quiz_$quizId.stars") ?: 0L).toInt()
            val newQuizStars = (currentStars - 1).coerceAtLeast(0)

            fun deductTotalStar() {
                totalStarsRef.get().addOnSuccessListener { totalDoc ->
                    val totalStars = (totalDoc.getLong("total") ?: 0L).toInt()
                    val newTotalStars = (totalStars - 1).coerceAtLeast(0)
                    totalStarsRef.set(mapOf("total" to newTotalStars), SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d("Firestore", "Total stars decreased to $newTotalStars")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error updating total stars", e)
                        }
                }
            }

            if (currentStars > 0) {
                quizStarsRef.update("quiz_$quizId.stars", newQuizStars)
                    .addOnSuccessListener {
                        Log.d("Firestore", "Quiz_$quizId stars decreased to $newQuizStars")
                        deductTotalStar()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error updating quiz stars", e)
                    }

            } else {
                Log.d("Firestore", "Quiz_$quizId already has 0 stars. Searching for another source to deduct...")

                quizStarsRef.get().addOnSuccessListener { allQuizDocs ->
                    val allQuizStars = allQuizDocs.data?.toMutableMap() ?: mutableMapOf()


                    val otherQuizEntry = allQuizStars.entries.find { entry ->
                        val starsMap = entry.value as? Map<*, *>
                        val starsValue = (starsMap?.get("stars") as? Long) ?: 0L
                        starsValue > 0
                    }

                    if (otherQuizEntry != null) {
                        val quizKey = otherQuizEntry.key
                        val starsMap = otherQuizEntry.value as? Map<*, *>
                        val starsValue = (starsMap?.get("stars") as? Long) ?: 0L
                        val newVal = (starsValue - 1).coerceAtLeast(0)

                        quizStarsRef.update("$quizKey.stars", newVal)
                            .addOnSuccessListener {
                                Log.d("Firestore", "Deducted 1 from $quizKey instead (new value: $newVal)")
                                Toast.makeText(this, "1 star deducted from $quizKey instead!", Toast.LENGTH_SHORT).show()
                                deductTotalStar()
                            }
                    } else {
                        exerciseStarsRef.get().addOnSuccessListener { exerciseDoc ->
                            val allExerciseStars = exerciseDoc.data?.toMutableMap() ?: mutableMapOf()
                            val otherExerciseEntry = allExerciseStars.entries.find { entry ->
                                val starsMap = entry.value as? Map<*, *>
                                val starsValue = (starsMap?.get("stars") as? Long) ?: 0L
                                starsValue > 0
                            }

                            if (otherExerciseEntry != null) {
                                val exerciseKey = otherExerciseEntry.key
                                val starsMap = otherExerciseEntry.value as? Map<*, *>
                                val starsValue = (starsMap?.get("stars") as? Long) ?: 0L
                                val newVal = (starsValue - 1).coerceAtLeast(0)

                                exerciseStarsRef.update("$exerciseKey.stars", newVal)
                                    .addOnSuccessListener {
                                        Log.d("Firestore", "Deducted 1 from $exerciseKey instead (new value: $newVal)")
                                        Toast.makeText(this, "1 star deducted from $exerciseKey instead!", Toast.LENGTH_SHORT).show()
                                        deductTotalStar()
                                    }
                            } else {
                                Log.d("Firestore", "No other stars available to deduct from.")
                                Toast.makeText(this, "No other stars available to deduct!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }


            firestore.collection("users")
                .document(uid)
                .collection("quizResults")
                .document("quiz_$quizId")
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Previous results cleared!", Toast.LENGTH_SHORT).show()

                    with(sharedPreferences.edit()) {
                        remove("quizCompleted_$quizId")
                        remove("quizResult_$quizId")
                        remove("quizScore_$quizId")
                        remove("quizProgress_$quizId")
                        apply()
                    }

                    binding?.btnSubmit?.visibility = View.VISIBLE
                    binding?.btnRetake?.visibility = View.GONE
                    binding?.tvQuizScore?.text = "0 / ${quizModule?.questions?.size ?: 0}"

                    quizAdapter.resetQuiz()
                    binding?.recyclerView?.scrollToPosition(0)

                    binding?.btnSubmit?.setOnClickListener {
                        checkAnswers()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this,"Failed to reset quiz!", Toast.LENGTH_SHORT).show()
                }

        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error fetching quiz stars", e)
        }
    }


    override fun onBackPressed() {
        val quizContainer = findViewById<FrameLayout>(R.id.quizFragmentContainer)
        if (quizContainer.visibility == View.VISIBLE) {
            quizContainer.visibility = View.GONE
            findViewById<ScrollView>(R.id.svQuizDetail).visibility = View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }
}
