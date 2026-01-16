package com.example.jsmart

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.jsmart.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserGreetings()
        updateAllProgress()
        fetchRandomProgrammingQuote()
        loadUserProgressMessage()

        binding.btnBack.setOnClickListener {
            navigateToDefaultScreen()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateToDefaultScreen()
                }
            }
        )
    }

    private fun navigateToDefaultScreen() {
        val defaultIntent = Intent(requireContext(), DefaultScreen::class.java)
        startActivity(defaultIntent)
        requireActivity().finish()
    }

    private fun loadUserProgressMessage() {
        val uid = auth.currentUser?.uid ?: run {
            _binding?.tvOverallProgress?.text = "Please sign in to see your progress."
            return
        }

        val totalModules = 13

        val userRef = db.collection("users").document(uid)

        userRef.collection("lessons").get().addOnSuccessListener { lessonDocs ->
            val completedLessons = lessonDocs.count { it.getLong("progress") == 1L }

            userRef.collection("quizResults").get().addOnSuccessListener { quizDocs ->
                val completedQuizzes = quizDocs.count { it.getLong("progress") == 1L }

                userRef.collection("exercise_feedback")
                    .whereEqualTo("progress", 1)
                    .get()
                    .addOnSuccessListener { exerciseDocs ->

                        if (!isAdded || _binding == null) return@addOnSuccessListener

                        val completedExercises = exerciseDocs.size()

                        if (
                            completedLessons >= totalModules &&
                            completedQuizzes >= totalModules &&
                            completedExercises >= totalModules
                        ) {
                            binding.tvOverallProgress.text =
                                "Congratulations on completing the JavaScript course! 🎉"
                            return@addOnSuccessListener
                        }


                        if (!exerciseDocs.isEmpty) {
                            val lastCompleted = exerciseDocs.last()
                            val lastTitle =
                                lastCompleted.getString("exerciseTitle")?.lowercase()
                                    ?: "an introduction"

                            val phrases = listOf(
                                "You're doing great in",
                                "Awesome job mastering",
                                "Fantastic progress in",
                                "You nailed"
                            )

                            val endings = listOf(
                                "Keep up the amazing work!",
                                "You're truly improving each day!",
                                "Excellent dedication — stay consistent!",
                                "Your progress is inspiring!"
                            )

                            binding.tvOverallProgress.text =
                                "${phrases.random()} $lastTitle! ${endings.random()}"
                        } else {
                            binding.tvOverallProgress.text =
                                "Let's start learning JavaScript today!"
                        }
                    }
            }
        }
    }


    private fun loopFetchProgress(collectionName: String, callback: (Int) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(uid)
            .collection(collectionName)
            .whereEqualTo("progress", 1)
            .get()
            .addOnSuccessListener { docs ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                callback(docs.size())
            }
            .addOnFailureListener {
                callback(0)
            }
    }

    private fun updateAllProgress() {
        loopFetchProgress("lessons") { lessonsDone ->
            loopFetchProgress("quizResults") { quizzesDone ->
                loopFetchProgress("exercise_feedback") { exercisesDone ->

                    val totalLessons = 13
                    val totalQuizzes = 13
                    val totalExercises = 13
                    val totalItems = totalLessons + totalQuizzes + totalExercises
                    val completedItems = lessonsDone + quizzesDone + exercisesDone

                    val overallPercent = if (totalItems > 0)
                        (completedItems * 100) / totalItems else 0

                    if (!isAdded || _binding == null) return@loopFetchProgress

                    _binding?.let { bind ->
                        bind.lessonsProgressBar.progress =
                            if (totalLessons > 0) (lessonsDone * 100) / totalLessons else 0
                        bind.quizProgressBar.progress =
                            if (totalQuizzes > 0) (quizzesDone * 100) / totalQuizzes else 0
                        bind.exercisesProgressBar.progress =
                            if (totalExercises > 0) (exercisesDone * 100) / totalExercises else 0
                        bind.overallProgressBar.progress = overallPercent
                    }
                }
            }
        }
    }

    private fun loadUserGreetings() {
        val firebaseUser = auth.currentUser
        firebaseUser?.let {
            val userRef = db.collection("users").document(it.uid)
            userRef.get().addOnSuccessListener { document ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                val name = document.getString("name") ?: "Guest"
                binding.tvGreetings.text = "$name's Progress Summary"
            }.addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                binding.tvGreetings.text = "Guest's Progress Summary"
            }
        } ?: run {
            _binding?.tvGreetings?.text = "Guest's Progress Summary"
        }
    }

    private fun fetchRandomProgrammingQuote() {
        val sharedPref = requireContext().getSharedPreferences("daily_quote", android.content.Context.MODE_PRIVATE)
        val lastFetchedDate = sharedPref.getString("last_date", null)
        val savedQuote = sharedPref.getString("quote", null)

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

        if (lastFetchedDate == today && !savedQuote.isNullOrEmpty()) {
            _binding?.tvQuotes?.text = savedQuote
            Log.d("QUOTE_API", "Using cached quote for $today")
            return
        }

        Log.d("QUOTE_API", "Fetching new quote... Today: $today, Last fetched: $lastFetchedDate")

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.QUOTES_API_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(QuotesApi::class.java)

        api.getRandomQuote().enqueue(object : Callback<ProgrammingQuote> {
            override fun onResponse(call: Call<ProgrammingQuote>, response: Response<ProgrammingQuote>) {
                if (!isAdded || _binding == null) return
                Log.d("QUOTE_API", "Response code: ${response.code()}")
                if (response.isSuccessful && response.body() != null) {
                    val quote = response.body()!!
                    val formattedQuote = "\"${quote.en}\"\n\n– ${quote.author}"

                    sharedPref.edit()
                        .putString("last_date", today)
                        .putString("quote", formattedQuote)
                        .apply()

                    binding.tvQuotes.text = formattedQuote
                    Log.d("QUOTE_API", "New quote saved and displayed.")
                } else {
                    showFallbackQuote(savedQuote)
                    Log.e("QUOTE_API", "Response failed: Code ${response.code()}, showing fallback quote.")
                }
            }

            override fun onFailure(call: Call<ProgrammingQuote>, t: Throwable) {
                if (!isAdded || _binding == null) return
                showFallbackQuote(savedQuote)
                Log.e("QUOTE_API", "Network error: ${t.localizedMessage}, showing fallback quote.")
            }
        })
    }

    private fun showFallbackQuote(savedQuote: String?) {
        val fallbackQuotes = listOf(
            "\"Talk is cheap. Show me the code.\" – Linus Torvalds",
            "\"Programs must be written for people to read, and only incidentally for machines to execute.\" – Harold Abelson",
            "\"First, solve the problem. Then, write the code.\" – John Johnson",
            "\"Experience is the name everyone gives to their mistakes.\" – Oscar Wilde"
        )
        val randomFallback = fallbackQuotes.random()
        _binding?.tvQuotes?.text = savedQuote ?: randomFallback
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
