package com.example.jsmart

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.jsmart.databinding.ActivityCheckDetailBinding
import com.example.jsmart.databinding.ActivityEditProfileScreenBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson

class CheckDetailActivity : AppCompatActivity() {
        private lateinit var auth: FirebaseAuth
        private val db = FirebaseFirestore.getInstance()
        private var userListener: ListenerRegistration? = null
        private lateinit var binding: ActivityCheckDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        binding = ActivityCheckDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)



        val exerciseId = intent.getStringExtra("exerciseId") ?: "exercise_1"
        loadUserFeedbackData(exerciseId)

        binding.btnBack.setOnClickListener {
            val currentExercise = exerciseId.removePrefix("exercise_").substringBefore("_retry").toIntOrNull() ?: 0
            val intent = Intent(this, ExerciseDetailActivity::class.java)
            intent.putExtra("exercise_id", currentExercise)
            startActivity(intent)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentExercise = exerciseId.removePrefix("exercise_").substringBefore("_retry").toIntOrNull() ?: 0
                val intent = Intent(this@CheckDetailActivity, ExerciseDetailActivity::class.java)
                intent.putExtra("exercise_id", currentExercise)
                startActivity(intent)
                finish()
            }
        })



        binding.btnNext.setOnClickListener {
            val currentLesson = exerciseId.removePrefix("exercise_").substringBefore("_retry").toIntOrNull() ?: 0
            val nextLesson = currentLesson + 1

            val intent = Intent(this, LessonDetailActivity::class.java)
            intent.putExtra("lesson_id", nextLesson)
            startActivity(intent)
            finish()
        }


    }




    private fun loadUserFeedbackData(exerciseId: String) {
        val user = auth.currentUser ?: return


        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { userDoc ->
                if (userDoc != null && userDoc.exists()) {
                    val firstName = userDoc.getString("name") ?: ""
                    val lastName = userDoc.getString("lastName") ?: ""
                    binding.tvuserName.text = "$firstName $lastName"


                }
            }





        db.collection("users")
            .document(user.uid)
            .collection("exercise_feedback")
            .document(exerciseId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {

                    binding.tvmoduleName.text = document.getString("exerciseTitle") ?: "No Module"

                    binding.tvStatus.text = document.getString("aiPassed") ?: "No Status"
                    binding.tvexerciseScore.text = document.getLong("aiGrade")?.toString() ?: "0"

                    binding.tvAiSummary.text = document.getString("aiSummary") ?: "No summary"
                    binding.tvOverallGrade.text = document.getString("aiStars") ?: "None"
                    binding.tvOverallGradeDescription.text = document.getString("aiDescription") ?: "None"

                    val improvements = document.get("aiImprovements") as? List<String> ?: emptyList()
                    val recommendations = document.get("aiRecommendations") as? List<String> ?: emptyList()
                    val strengths = document.get("aiStrengths") as? List<String> ?: emptyList()

                    binding.tvImprovements.text = improvements.joinToString("\n• ", prefix = "• ")
                    binding.tvRecommendations.text = recommendations.joinToString("\n• ", prefix = "• ")
                    binding.tvStrengths.text = strengths.joinToString("\n• ", prefix = "• ")
                } else {
                    Toast.makeText(this, "No details found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load details.", Toast.LENGTH_SHORT).show()
            }
    }
    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
    }
}