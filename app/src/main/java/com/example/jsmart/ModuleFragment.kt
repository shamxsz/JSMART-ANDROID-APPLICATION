package com.example.jsmart

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class ModuleFragment : Fragment() {
    private var firebaseUserId: String? = null
    private val firestore = FirebaseFirestore.getInstance()
    private var rootView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_module, container, false)
        firebaseUserId = FirebaseAuth.getInstance().currentUser?.uid

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

        val lessonIds = listOf(
            R.id.Lesson1, R.id.Lesson2, R.id.Lesson3,
            R.id.Lesson4, R.id.Lesson5, R.id.Lesson6,
            R.id.Lesson7, R.id.Lesson8, R.id.Lesson9,
            R.id.Lesson10, R.id.Lesson11, R.id.Lesson12,
            R.id.Lesson13
        )


        for ((index, lessonId) in lessonIds.withIndex()) {
            val lessonCard = rootView?.findViewById<CardView>(lessonId)


            lessonCard?.setOnClickListener {
                openLessonDetail(index + 1)
            }

            lessonCard?.alpha = 0.5f
            firebaseUserId?.let { uid ->

                firestore.collection("users")
                    .document(uid)
                    .collection("lessons")
                    .document("lesson_${index + 1}")
                    .addSnapshotListener { document, error ->
                        if (error != null) return@addSnapshotListener
                        if (document != null && document.exists()) {
                            val progress = document.getLong("progress")?.toInt() ?: 0
                            lessonCard?.alpha = if (progress == 1) 1f else 0.5f
                        }
                    }
            }
        }
        return rootView
    }






    private fun openLessonDetail(lessonId: Int) {
        val intent = Intent(activity, LessonDetailActivity::class.java)
        intent.putExtra("lesson_id", lessonId)
        startActivity(intent)
    }

}
