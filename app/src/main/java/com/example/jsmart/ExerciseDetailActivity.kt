package com.example.jsmart
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.amrdeveloper.codeview.CodeView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.InputStream
import java.util.regex.Pattern


class ExerciseDetailActivity : BaseActivity() {

    private lateinit var codeView: CodeView
    private lateinit var btnRun: MaterialButton
    private lateinit var btnCheck: MaterialButton
    private lateinit var outputConsole: TextView
    private lateinit var btnRetake: MaterialButton
    private lateinit var tvDoneAct: TextView
    private lateinit var tvRetakeAct: TextView


    private val JDoodleBaseURL = BuildConfig.JDOODLE_URL
    private val clientId = BuildConfig.JDOODLE_CLIENT_ID
    private val clientSecret = BuildConfig.JDOODLE_CLIENT_SECRET

    private val PASSING_SCORE = 70
    private val GEMINI_API_KEY = BuildConfig.MY_GEMINI_API_KEY

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val gson by lazy { Gson() }

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-pro",
            apiKey = GEMINI_API_KEY
        )
    }

    private var retryCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_detail)

        val tvExerciseTitle = findViewById<TextView>(R.id.tvExerciseTitle)
        val tvExerciseContent = findViewById<TextView>(R.id.tvExerciseContent)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnFeedback = findViewById<ImageButton>(R.id.btnFeedback)

        val exerciseId = intent.getIntExtra("exercise_id", 0)
        val jsonArray = loadJSONFromAssets()
        val exercise = jsonArray?.let { getExerciseById(it, exerciseId) }

        if (exercise != null) {
            tvExerciseTitle.text = exercise.title
            tvExerciseContent.text = exercise.task
        } else {
            tvExerciseTitle.text = "Exercise Not Found"
            tvExerciseContent.text = "No content available for this exercise."
        }

        val user = auth.currentUser
        if (user != null) {
            val feedbackRef = firestore.collection("users")
                .document(user.uid)
                .collection("exercise_feedback")
                .document("exercise_$exerciseId")


            feedbackRef.get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val isRetake = doc.getBoolean("isRetake") ?: false
                    if (isRetake) {
                        val nextTask = (doc.get("nextTask") as? Map<*, *>)?.get("task") as? String
                        if (!nextTask.isNullOrBlank()) {
                            tvExerciseContent.text = nextTask
                        }
                    }
                }
            }
        }

        codeView = findViewById(R.id.codeView)
        btnRun = findViewById(R.id.btnRun)
        btnCheck = findViewById(R.id.btnCheck)
        outputConsole = findViewById(R.id.outputConsole)
        btnRetake = findViewById(R.id.btnRetake)
        tvDoneAct = findViewById(R.id.tvDoneAct)
        tvRetakeAct = findViewById(R.id.tvRetakeAct)

        updateUIFromFeedback(exerciseId)

        btnCheck.visibility = View.VISIBLE

        loadSavedAnswer(exerciseId)


        val syntaxPatterns = mutableMapOf<Pattern, Int>()
        if (exercise != null) {
            tvExerciseTitle.text = exercise.title
            tvExerciseContent.text = exercise.task
        } else {
            tvExerciseTitle.text = "Exercise Not Found"
            tvExerciseContent.text = "No content available for this exercise."
        }

        markExerciseAsPending(exerciseId)

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }


        syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)
        syntaxPatterns[Pattern.compile("\".*?\"|'.*?'|`.*?`")] = Color.rgb(180, 210, 255) // soft sky blue for strings
        syntaxPatterns[Pattern.compile("\\b\\d+\\.?\\d*\\b")] = Color.rgb(255, 190, 120) // warm orange
        syntaxPatterns[Pattern.compile("\\b(console\\.log|console\\.error|console\\.warn|console\\.info|console\\.debug)\\b")] =
            Color.rgb(255, 180, 120) // soft orange for logs
        syntaxPatterns[Pattern.compile("\\b(var|let|const|function|if|else|return|for|while|switch|case|break|continue|default|try|catch|finally|throw|new|delete|typeof|instanceof|in|do|async|await|yield|class|extends|super|this|import|export|from|as|static|get|set|constructor|debugger|with)\\b")] =
            Color.rgb(255, 140, 160) // rose pink — bright but not neon
        syntaxPatterns[Pattern.compile("\\b(Number|String|Boolean|Object|Array|Symbol|Math|Date|RegExp|JSON|Promise|Set|Map|WeakSet|WeakMap|BigInt)\\b")] =
            Color.rgb(255, 220, 120) // soft gold
        syntaxPatterns[Pattern.compile("\\b(console|document|window|alert|prompt|confirm|parseInt|parseFloat|isNaN|isFinite|decodeURI|encodeURI|setTimeout|setInterval|clearTimeout|clearInterval|localStorage|sessionStorage|fetch|require)\\b")] =
            Color.rgb(130, 210, 150) // soft green
        syntaxPatterns[Pattern.compile("\\b(length|toUpperCase|toLowerCase|push|pop|slice|splice|map|filter|reduce|forEach|join|replace|charAt|charCodeAt|includes|indexOf|addEventListener|removeEventListener|querySelector|querySelectorAll|getElementById|getElementsByClassName|getElementsByTagName|innerHTML|innerText|style|value)\\b")] =
            Color.rgb(120, 180, 255) // soft blue
        syntaxPatterns[Pattern.compile("\\b(true|false|null|undefined|NaN|Infinity)\\b")] =
            Color.rgb(190, 150, 255) // soft violet
        syntaxPatterns[Pattern.compile("[+\\-*/%=!<>&|^~?:]+")] =
            Color.rgb(255, 150, 150) // soft red
        syntaxPatterns[Pattern.compile("</?\\w+.*?>")] =
            Color.rgb(255, 180, 160) // soft coral for tags
        syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)



        codeView.setTextColor(Color.WHITE)
        codeView.setEnableLineNumber(true)
        codeView.setLineNumberTextSize(22f)
        codeView.setLineNumberTextColor(Color.WHITE)
        codeView.setEnableAutoIndentation(true)
        codeView.setEnableRelativeLineNumber(false)
        codeView.setEnableHighlightCurrentLine(true)
        codeView.setHighlightCurrentLineColor(Color.GRAY)
        codeView.setSyntaxPatternsMap(syntaxPatterns)
        codeView.setHighlightWhileTextChanging(true)
        codeView.reHighlightSyntax()
        codeView.setTextSize(13F)
        codeView.setPadding(50, 10, 10, 10)


        btnRun.setOnClickListener {
            val userCode = codeView.text.toString()
            if (userCode.isNotBlank()) executeCode(userCode)
            else outputConsole.text = "Please enter some code before running."
        }

        btnFeedback.setOnClickListener {
            val exerciseKey = if (retryCount == 0)
                "exercise_$exerciseId"
            else
                "exercise_${exerciseId}_retry$retryCount"

            val intent = Intent(this, CheckDetailActivity::class.java)
            intent.putExtra("exerciseId", exerciseKey)
            startActivity(intent)
            finish()
        }


        btnCheck.setOnClickListener {
            val user = auth.currentUser ?: return@setOnClickListener
            val title = tvExerciseTitle.text?.toString()?.trim().orEmpty()
            val task = tvExerciseContent.text?.toString()?.trim().orEmpty()
            val answerCode = codeView.text?.toString()?.trim().orEmpty()

            if (title.isBlank() || task.isBlank() || answerCode.isBlank()) {
                Toast.makeText(this, "Exercise data or answer is missing.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            btnCheck.isEnabled = false
            showProgressBar()

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val exerciseKey =
                        if (retryCount == 0) "exercise_$exerciseId" else "exercise_${exerciseId}_retry$retryCount"

                    val docRef = firestore.collection("users")
                        .document(user.uid)
                        .collection("exercises")
                        .document(exerciseKey)

                    val submission = hashMapOf(
                        "exerciseId" to exerciseId,
                        "exerciseTitle" to title,
                        "exerciseTask" to task,
                        "exerciseAnswer" to answerCode,
                        "submittedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(submission).await()

                    val assessment = assessWithGemini(
                        exerciseTitle = title,
                        exerciseTask = task,
                        userCode = answerCode,
                        passingScore = PASSING_SCORE
                    )

                    val status = if (assessment.passed) "Passed" else "Failed"

                    val starCount = starsStringToNumber(assessment.stars)
                    uploadExerciseStars(exerciseId, starCount)

                    val feedbackRef = firestore.collection("users")
                        .document(user.uid)
                        .collection("exercise_feedback")
                        .document("exercise_$exerciseId")

                    val feedbackPayload = hashMapOf(
                        "exerciseId" to exerciseId,
                        "exerciseTitle" to title,
                        "aiGrade" to assessment.grade,
                        "aiPassed" to status,
                        "aiSummary" to assessment.summary,
                        "aiStrengths" to assessment.strengths,
                        "aiImprovements" to assessment.improvements,
                        "aiRecommendations" to assessment.recommendations,
                        "aiStars" to assessment.stars,
                        "aiDescription" to assessment.description,
                        "exerciseTask" to task,
                        "exerciseAnswer" to answerCode,
                        "submittedAt" to FieldValue.serverTimestamp(),
                        "gradedAt" to FieldValue.serverTimestamp(),
                        "progress" to 1
                    )

                    assessment.nextTask?.let {
                        feedbackPayload["nextTask"] = mapOf("task" to it.task)
                    }


                    feedbackRef.set(
                        feedbackPayload,
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()

                    docRef.update(
                        mapOf(
                            "aiGrade" to assessment.grade,
                            "aiPassed" to status,
                            "aiStrengths" to assessment.strengths,
                            "aiImprovements" to assessment.improvements,
                            "aiRecommendations" to assessment.recommendations,
                            "aiStars" to assessment.stars,
                            "aiDescription" to assessment.description,
                            "aiSummary" to assessment.summary,
                            "gradedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()

                    if (assessment.passed) {
                        feedbackRef.update(
                            mapOf(
                                "status" to "completed",
                                "completedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }

                    if (assessment.passed) {
                        tvRetakeAct.visibility = View.GONE
                        btnRetake.visibility = View.GONE
                        tvDoneAct.visibility = View.GONE
                        btnCheck.visibility = View.GONE
                    } else {
                        tvRetakeAct.visibility = View.VISIBLE
                        btnRetake.visibility = View.VISIBLE
                        tvDoneAct.visibility = View.GONE
                        btnCheck.visibility = View.GONE
                    }


                    ScoreResultDialog(
                        assessment.grade,
                        100,
                        onFinish = {
                            val exerciseKeyFinal = if (retryCount == 0)
                                "exercise_$exerciseId"
                            else
                                "exercise_${exerciseId}_retry$retryCount"

                            val intent = Intent(this@ExerciseDetailActivity, CheckDetailActivity::class.java)
                            intent.putExtra("exerciseId", exerciseKeyFinal)
                            startActivity(intent)
                            finish()
                        },
                        aiStars = assessment.stars
                    ).show(supportFragmentManager, "ScoreDialog")


                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(
                        this@ExerciseDetailActivity,
                        "Submit failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    btnCheck.isEnabled = true
                    hideProgressBar()
                }
            }
        }

        btnRetake.setOnClickListener {
            val user = auth.currentUser ?: return@setOnClickListener
            val feedbackRef = firestore.collection("users")
                .document(user.uid)
                .collection("exercise_feedback")
                .document("exercise_$exerciseId")


            feedbackRef.update("isRetake", true)
                .addOnSuccessListener {

                    feedbackRef.get().addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val nextTask =
                                (doc.get("nextTask") as? Map<*, *>)?.get("task") as? String
                            if (!nextTask.isNullOrBlank()) {
                                tvExerciseContent.text = nextTask
                                Toast.makeText(this, "Next task loaded!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "No next task found.", Toast.LENGTH_SHORT)
                                    .show()
                            }


                            retryCount++

                            val exerciseStarsRef = firestore
                                .collection("users")
                                .document(user.uid)
                                .collection("starCollection")
                                .document("exerciseStars")

                            val quizStarsRef = firestore
                                .collection("users")
                                .document(user.uid)
                                .collection("starCollection")
                                .document("quizStars")

                            val totalStarsRef = firestore
                                .collection("users")
                                .document(user.uid)
                                .collection("starCollection")
                                .document("totalStars")

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


                            exerciseStarsRef.get().addOnSuccessListener { exerciseDoc ->
                                val currentStars = (exerciseDoc.getLong("exercise_$exerciseId.stars") ?: 0L).toInt()
                                val newExerciseStars = (currentStars - 1).coerceAtLeast(0)

                                if (currentStars > 0) {
                                    exerciseStarsRef.update("exercise_$exerciseId.stars", newExerciseStars)
                                        .addOnSuccessListener {
                                            Log.d("Firestore", "Exercise_$exerciseId stars decreased to $newExerciseStars")
                                            Toast.makeText(this, "1 star deducted from exercise!", Toast.LENGTH_SHORT).show()
                                            deductTotalStar()
                                        }
                                } else {

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

                                        Log.d("Firestore", "No exercise stars left. Checking quiz stars...")

                                        quizStarsRef.get().addOnSuccessListener { quizDoc ->
                                            val allQuizStars = quizDoc.data?.toMutableMap() ?: mutableMapOf()
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
                                                Log.d("Firestore", "No quiz stars available to deduct.")
                                                Toast.makeText(this, "No stars available to deduct!", Toast.LENGTH_SHORT).show()
                                            }
                                        }.addOnFailureListener { e ->
                                            Log.e("Firestore", "Failed to fetch quizStars", e)
                                        }
                                    }
                                }
                            }.addOnFailureListener { e ->
                                Log.e("Firestore", "Failed to fetch exerciseStars", e)
                            }


                            val resetData = mapOf(
                                "exerciseAnswer" to FieldValue.delete(),
                                "aiPassed" to FieldValue.delete(),
                                "aiGrade" to FieldValue.delete()
                            )

                            feedbackRef.update(resetData).addOnSuccessListener {

                                codeView.setText("")
                                outputConsole.setText("")
                                btnCheck.visibility = View.VISIBLE
                                tvDoneAct.visibility = View.VISIBLE
                                btnRetake.visibility = View.GONE
                                tvRetakeAct.visibility = View.GONE
                            }
                        }
                    }
                }
        }
    }

    private fun markExerciseAsPending(exerciseId: Int) {
        val user = auth.currentUser ?: return

        val feedbackRef = firestore.collection("users")
            .document(user.uid)
            .collection("exercise_feedback")
            .document("exercise_$exerciseId")

        feedbackRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                val data = mapOf(
                    "exerciseId" to exerciseId,
                    "status" to "in-progress",
                )
                feedbackRef.set(data)
            } else {
                val status = doc.getString("status")
                if (status != "completed") {
                    feedbackRef.update(
                        mapOf(
                            "status" to "in-progress",
                        )
                    )
                }
            }
        }
    }



        private fun starsStringToNumber(stars: String): Int {
        return when {
            stars.startsWith("⭐⭐⭐⭐⭐") -> 5
            stars.startsWith("⭐⭐⭐⭐") -> 4
            stars.startsWith("⭐⭐⭐") -> 3
            stars.startsWith("⭐⭐") -> 2
            stars.startsWith("⭐") -> 1
            else -> 0
        }
    }


    private fun uploadExerciseStars(exerciseId: Int, stars: Int) {
        val uid = auth.currentUser?.uid ?: return
        val exerciseStarsRef = firestore
            .collection("users")
            .document(uid)
            .collection("starCollection")
            .document("exerciseStars")

        val updateData = mapOf(
            "exercise_$exerciseId" to mapOf("stars" to stars)
        )

        exerciseStarsRef.set(updateData, com.google.firebase.firestore.SetOptions.merge())
    }

    private suspend fun assessWithGemini(
        exerciseTitle: String,
        exerciseTask: String,
        userCode: String,
        passingScore: Int
    ): AiAssessment = withContext(Dispatchers.IO) {
        val systemJsonContract = """
            You are an automated code assessor for a JavaScript learning app.
            Output ONLY valid JSON with this exact schema:
    
    {
      "grade": number (0-100),
      "passed": boolean,         
      "stars": string,           // e.g. "⭐⭐⭐⭐ Excellent"
      "description": string,     // e.g. "Strong grasp of the lesson, just a few minor mistakes."
      "summary": string,
      "strengths": [string],
      "improvements": [string],
      "recommendations": [string],
      "nextTask": {
        "task": string
      } | null
    }
    
    Rules:
    - "passed" must be true only if grade >= $passingScore, otherwise false.
    - "stars" and "description" must strictly follow this mapping:
      * 95–100 → 
        stars: "⭐⭐⭐⭐⭐ Mastered"
        description: "Top performance, shows full understanding."
      * 85–94  → 
        stars: "⭐⭐⭐⭐ Excellent"
        description: "Strong grasp of the lesson, just a few minor mistakes."
      * 75–84  → 
        stars: "⭐⭐⭐ Very Good"
        description: "Good progress, needs a little more practice."
      * 65–74  → 
        stars: "⭐⭐ Good"
        description: "Understands some concepts but needs review."
      * 0–64   → 
        stars: "⭐ Keep Practicing"
        description: "Still learning, but on the right track."
    - Provide actionable "improvements" and "recommendations" even passed or failed.
    - If result == "Failed", include a "nextTask" that is simpler and focused on the weakest skill.But make sure it is align on the topic.
    - Do not include ANY text outside the JSON.
    """.trimIndent()


        val userContext = """
            Exercise Title: $exerciseTitle
            Exercise Task: $exerciseTask

            Student Submission (JavaScript):
            ```
            $userCode
            ```
        """.trimIndent()

        val prompt = "$systemJsonContract\n\n$userContext"

        val response = generativeModel.generateContent(prompt)
        val text = response.text ?: throw IllegalStateException("Empty AI response")

        val clean = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val type = object : TypeToken<AiAssessment>() {}.type
        try {
            gson.fromJson<AiAssessment>(clean, type)
        } catch (e: Exception) {
            val obj = JsonParser.parseString(clean)
            if (!obj.isJsonObject) throw e
            gson.fromJson<AiAssessment>(obj, type)
        }
    }


    private fun executeCode(code: String) {
        val request = JDoodleRequest(clientId, clientSecret, code, "nodejs", "4")
        ApiClient.apiService.executeCode(request).enqueue(object : Callback<JDoodleResponse> {
            override fun onResponse(
                call: Call<JDoodleResponse>,
                response: Response<JDoodleResponse>
            ) {
                outputConsole.text = if (response.isSuccessful) {
                    response.body()?.output ?: "No output"
                } else {
                    "Execution failed: ${response.errorBody()?.string()}"
                }
            }

            override fun onFailure(call: Call<JDoodleResponse>, t: Throwable) {
                outputConsole.text = "Error: ${t.message}"
            }
        })
    }

    private fun loadSavedAnswer(exerciseId: Int) {
        val user = auth.currentUser ?: return

        firestore.collection("users")
            .document(user.uid)
            .collection("exercise_feedback")
            .document("exercise_$exerciseId")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val savedAnswer = document.getString("exerciseAnswer") ?: ""
                    if (savedAnswer.isNotBlank()) {
                        codeView.setText(savedAnswer)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load saved answer.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun loadJSONFromAssets(): JSONArray? {
        return try {
            val inputStream: InputStream = assets.open("exercise.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer, Charsets.UTF_8)
            JSONObject(jsonString).getJSONArray("exercises")
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    private fun getExerciseById(jsonArray: JSONArray, id: Int): Exercise? {
        for (i in 0 until jsonArray.length()) {
            val exercise = jsonArray.getJSONObject(i)
            if (exercise.getInt("id") == id) {
                val title = exercise.getString("title")
                val task = exercise.getString("task")
                return Exercise(id, title, task)
            }
        }
        return null
    }

    private fun updateUIFromFeedback(exerciseId: Int) {
        val user = auth.currentUser ?: return

        firestore.collection("users")
            .document(user.uid)
            .collection("exercise_feedback")
            .document("exercise_$exerciseId")
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    btnCheck.visibility = View.VISIBLE
                    btnRetake.visibility = View.GONE
                    tvDoneAct.visibility = View.VISIBLE
                    tvRetakeAct.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val aiPassed = doc.getString("aiPassed")

                when (aiPassed) {
                    "Passed" -> {
                        btnCheck.visibility = View.GONE
                        btnRetake.visibility = View.GONE
                        tvDoneAct.visibility = View.GONE
                        tvRetakeAct.visibility = View.GONE
                    }

                    "Failed" -> {
                        btnCheck.visibility = View.GONE
                        btnRetake.visibility = View.VISIBLE
                        tvDoneAct.visibility = View.GONE
                        tvRetakeAct.visibility = View.VISIBLE
                    }

                    else -> {
                        btnCheck.visibility = View.VISIBLE
                        btnRetake.visibility = View.GONE
                        tvDoneAct.visibility = View.VISIBLE
                        tvRetakeAct.visibility = View.GONE
                    }
                }
            }
    }


    override fun onDestroy() {
        super.onDestroy()
    }

}
