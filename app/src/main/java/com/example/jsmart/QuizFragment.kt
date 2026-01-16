package com.example.jsmart

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class QuizFragment : Fragment() {

    private lateinit var quizzes: List<QuizModule>
    private lateinit var sharedPreferences: SharedPreferences
    private var firebaseUserId: String? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_quiz, container, false)


        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val defaultIntent = Intent(requireContext(), DefaultScreen::class.java)
                    startActivity(defaultIntent)
                    requireActivity().finish()
                }
            }
        )

        firebaseUserId = FirebaseAuth.getInstance().currentUser?.uid
        firebaseUserId?.let { uid ->
            sharedPreferences = requireContext().getSharedPreferences("QuizPrefs_$uid", Context.MODE_PRIVATE)
        }

        loadQuizzesFromJson()
        return view
    }

    private fun refreshUI() {
        val quizIds = listOf(
            R.id.Quiz1, R.id.Quiz2, R.id.Quiz3, R.id.Quiz4, R.id.Quiz5,
            R.id.Quiz6, R.id.Quiz7, R.id.Quiz8, R.id.Quiz9, R.id.Quiz10,
            R.id.Quiz11, R.id.Quiz12, R.id.Quiz13
        )

        val scoreTextViews = listOf(
            R.id.tvScore1, R.id.tvScore2, R.id.tvScore3, R.id.tvScore4, R.id.tvScore5,
            R.id.tvScore6, R.id.tvScore7, R.id.tvScore8, R.id.tvScore9, R.id.tvScore10,
            R.id.tvScore11, R.id.tvScore12, R.id.tvScore13
        )

        val uid = firebaseUserId ?: return

        listeners.forEach { it.remove() }
        listeners.clear()

        for ((index, quizIdView) in quizIds.withIndex()) {
            if (::quizzes.isInitialized && index < quizzes.size) {
                val quiz = quizzes[index]
                val quizCard = view?.findViewById<CardView>(quizIdView)
                val scoreTextView = view?.findViewById<TextView>(scoreTextViews[index])

                quizCard?.apply {
                    isClickable = true
                    isEnabled = true
                    setOnClickListener { openQuizDetail(index) }
                }


                val listener = firestore.collection("users")
                    .document(uid)
                    .collection("quizResults")
                    .document("quiz_${quiz.id}")
                    .addSnapshotListener { snapshot, e ->

                        if (!isAdded || view == null) return@addSnapshotListener

                        if (e != null) {
                            val fallbackScore = getQuizScore(quiz.id) ?: 0
                            val fallbackCompleted = fallbackScore > 0 || isQuizCompleted(quiz.id)
                            scoreTextView?.text = "$fallbackScore / ${quiz.questions.size}"
                            quizCard?.alpha = if (fallbackCompleted) 1f else 0.5f
                            quizCard?.setBackgroundResource(R.drawable.lgnbtn)
                            return@addSnapshotListener
                        }

                        if (snapshot != null && snapshot.exists()) {
                            val quizResult = snapshot.toObject(QuizResult::class.java)
                            val score = quizResult?.score ?: 0
                            val isCompleted = quizResult?.progress == 1

                            scoreTextView?.text = "$score / ${quiz.questions.size}"
                            quizCard?.alpha = if (isCompleted) 1f else 0.5f
                            quizCard?.setBackgroundResource(R.drawable.lgnbtn)

                            sharedPreferences.edit()
                                .putBoolean("quizCompleted_${quiz.id}", isCompleted)
                                .putString("quizResult_${quiz.id}", Gson().toJson(quizResult))
                                .apply()
                        } else {
                            val localScore = getQuizScore(quiz.id) ?: 0
                            val localCompleted = localScore > 0 || isQuizCompleted(quiz.id)

                            scoreTextView?.text = "$localScore / ${quiz.questions.size}"
                            quizCard?.alpha = if (localCompleted) 1f else 0.5f
                            quizCard?.setBackgroundResource(R.drawable.lgnbtn)
                        }
                    }

                listeners.add(listener)
            }
        }
    }

    private fun openQuizDetail(quizIndex: Int) {
        if (::quizzes.isInitialized && quizIndex < quizzes.size) {
            val selectedQuiz = quizzes[quizIndex]
            val intent = Intent(activity, QuizDetailActivity::class.java)
            intent.putExtra("QUIZ_DATA", Gson().toJson(selectedQuiz))
            startActivity(intent)
        }
    }

    private fun loadQuizzesFromJson() {
        val context = context ?: return
        val json = readJsonFile(context, "quiz.json")
        val type = object : TypeToken<List<QuizModule>>() {}.type
        quizzes = Gson().fromJson(json, type)
    }

    private fun isQuizCompleted(quizId: Int): Boolean {
        return firebaseUserId?.let {
            sharedPreferences.getBoolean("quizCompleted_$quizId", false)
        } ?: false
    }

    private fun getQuizScore(quizId: Int): Int? {
        val context = context ?: return null
        val firebaseUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val sharedPreferences = context.getSharedPreferences("QuizPrefs_$firebaseUserId", Context.MODE_PRIVATE)
        val jsonResult = sharedPreferences.getString("quizResult_$quizId", null)

        return jsonResult?.let {
            val quizResult = Gson().fromJson(it, QuizResult::class.java)
            quizResult?.score
        }
    }

    private fun readJsonFile(context: Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    override fun onResume() {
        super.onResume()
        loadQuizzesFromJson()
        refreshUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listeners.forEach { it.remove() }
        listeners.clear()
    }
}
