package com.example.jsmart

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ExerciseFragment : Fragment() {

    private var firebaseUserId: String? = null
    private val firestore = FirebaseFirestore.getInstance()
    private var rootView: View? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_exercise, container, false)

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
        refreshUI()

        return rootView
    }

    private fun openExerciseDetail(exerciseIds: Int) {
        val intent = Intent(activity, ExerciseDetailActivity::class.java)
        intent.putExtra("exercise_id", exerciseIds)
        startActivity(intent)
    }

    private fun refreshUI() {
        val exerciseIds = listOf(
            R.id.Exercise1, R.id.Exercise2, R.id.Exercise3,
            R.id.Exercise4, R.id.Exercise5, R.id.Exercise6,
            R.id.Exercise7, R.id.Exercise8, R.id.Exercise9,
            R.id.Exercise10, R.id.Exercise11, R.id.Exercise12,
            R.id.Exercise13
        )

        val scoreTextViews = listOf(
            R.id.tvExerciseScore1,
            R.id.tvExerciseScore2,
            R.id.tvExerciseScore3,
            R.id.tvExerciseScore4,
            R.id.tvExerciseScore5,
            R.id.tvExerciseScore6,
            R.id.tvExerciseScore7,
            R.id.tvExerciseScore8,
            R.id.tvExerciseScore9,
            R.id.tvExerciseScore10,
            R.id.tvExerciseScore11,
            R.id.tvExerciseScore12,
            R.id.tvExerciseScore13
        )

        for ((index, exerciseId) in exerciseIds.withIndex()) {
            val exerciseCard = rootView?.findViewById<CardView>(exerciseId)
            val scoreTextView = rootView?.findViewById<TextView>(scoreTextViews[index])


            exerciseCard?.setOnClickListener {
                openExerciseDetail(index + 1)
            }

            exerciseCard?.alpha = 0.5f
            firebaseUserId?.let { uid ->
                firestore.collection("users")
                    .document(uid)
                    .collection("exercise_feedback")
                    .document("exercise_${index + 1}")
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val score = document.getLong("aiGrade")?.toInt() ?: 0
                            scoreTextView?.text = "$score / 100"

                            val progress = document.getString("status")

                            if (progress == "in-progress" || progress == "completed") {
                                exerciseCard?.alpha = 1f
                            } else {
                                exerciseCard?.alpha = 0.5f
                            }
                        }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            refreshUI()
        }, 300)
    }

}
