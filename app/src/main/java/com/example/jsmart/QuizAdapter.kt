package com.example.jsmart

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jsmart.databinding.ItemQuizQuestionBinding
import com.google.firebase.auth.FirebaseAuth

class QuizAdapter(
    private val context: Context,
    private val quizId: Int,
    private val questions: List<QuizQuestion>
) : RecyclerView.Adapter<QuizAdapter.QuizViewHolder>() {

    private val firebaseUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val sharedPreferences = firebaseUserId?.let {
        context.getSharedPreferences("QuizPrefs_$it", Context.MODE_PRIVATE)
    }
    private val selectedAnswers = mutableMapOf<Int, String>()
    private var showCorrectAnswers = false

    init {
        loadSavedAnswers()
    }

    inner class QuizViewHolder(val binding: ItemQuizQuestionBinding) : RecyclerView.ViewHolder(binding.root) {
        val questionText: TextView = binding.questionText
        val radioGroup: RadioGroup = binding.radioGroup
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val binding = ItemQuizQuestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuizViewHolder(binding)
    }


    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) {
        val question = questions[position]
        holder.questionText.text = question.question


        holder.radioGroup.setOnCheckedChangeListener(null)
        holder.radioGroup.removeAllViews()

        val correctAnswer = question.correctAnswer
        val selectedAnswer = selectedAnswers[position]

        question.options.forEach { option ->
            val context = holder.itemView.context
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)

            val radioButton = RadioButton(context).apply {
                text = option
                id = ViewGroup.generateViewId()
                setTextColor(typedValue.data)
                buttonTintList = ColorStateList.valueOf(typedValue.data)
                isChecked = selectedAnswer == option
                isEnabled = !showCorrectAnswers
            }


            holder.radioGroup.addView(radioButton)


            if (showCorrectAnswers) {
                if (option == correctAnswer) {
                    radioButton.setTextColor(Color.GREEN)
                    radioButton.buttonTintList = ColorStateList.valueOf(Color.GREEN)
                } else if (option == selectedAnswer) {
                    radioButton.setTextColor(Color.RED)
                    radioButton.buttonTintList = ColorStateList.valueOf(Color.RED)
                }
            }

            radioButton.setOnClickListener {
                if (!showCorrectAnswers) {
                    selectedAnswers[position] = option
                    saveAnswer(position, option)
                }
            }
        }
        if (unansweredList.contains(position)) {
            holder.itemView.setBackgroundResource(R.drawable.red_border)
        } else {
            holder.itemView.setBackgroundResource(R.drawable.default_border)
        }

    }


    override fun getItemCount() = questions.size

    fun getSelectedAnswers(): Map<Int, String> = selectedAnswers

    fun showCorrectAnswers() {
        showCorrectAnswers = true
        notifyDataSetChanged()
    }

    fun resetQuiz() {
        selectedAnswers.clear()

        firebaseUserId?.let {
            val editor = sharedPreferences?.edit()
            editor?.clear()
            editor?.apply()
        }

        showCorrectAnswers = false

        notifyDataSetChanged()
    }

    private var unansweredList: List<Int> = emptyList()

    fun markUnansweredQuestions(indices: List<Int>) {
        unansweredList = indices
        notifyDataSetChanged()
    }

    private fun saveAnswer(questionIndex: Int, answer: String) {
        firebaseUserId?.let {
            sharedPreferences?.edit()?.putString("$quizId-$questionIndex", answer)?.apply()
        }
    }

    private fun loadSavedAnswers() {
        firebaseUserId?.let {
            for (index in questions.indices) {
                selectedAnswers[index] = sharedPreferences?.getString("$quizId-$index", "") ?: ""
            }
        }
    }
    fun setUserAnswers(responses: List<Response>) {
        responses.forEachIndexed { index, response ->
            selectedAnswers[index] = response.userAnswer
        }
        notifyDataSetChanged()
    }



}
