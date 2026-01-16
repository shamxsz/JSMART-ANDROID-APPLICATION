package com.example.jsmart

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class ScoreResultDialog(
    private val score: Int,
    private val total: Int,
    private val onFinish: () -> Unit,
    private val aiStars: String? = null
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_result_quiz, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvScore = view.findViewById<TextView>(R.id.tvScore)
        val btnFinish = view.findViewById<Button>(R.id.btnFinish)

        tvScore.text = "Your Score: $score/$total"

        val stars = listOf(
            view.findViewById<ImageView>(R.id.star1),
            view.findViewById<ImageView>(R.id.star2),
            view.findViewById<ImageView>(R.id.star3),
            view.findViewById<ImageView>(R.id.star4),
            view.findViewById<ImageView>(R.id.star5)
        )


        val aiStarCount = aiStars?.toIntOrNull()


        val starCount = aiStarCount ?: when {
            score == total -> 5
            score >= total * 0.8 -> 4
            score >= total * 0.6 -> 3
            score >= total * 0.4 -> 2
            score > 0 -> 1
            else -> 0
        }


        val handler = Handler(Looper.getMainLooper())
        for (i in 0 until starCount) {
            handler.postDelayed({
                stars[i].visibility = View.VISIBLE
                val anim = ScaleAnimation(
                    0f, 1f, 0f, 1f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f
                )
                anim.duration = 400
                stars[i].startAnimation(anim)
            }, i * 500L)
        }

        btnFinish.setOnClickListener {
            onFinish.invoke()
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }
}
