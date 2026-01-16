package com.example.jsmart

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.jsmart.databinding.ActivityForgotPasswordBinding
import com.google.firebase.auth.FirebaseAuth

class ForgotPassword : BaseActivity() {
    private var binding:ActivityForgotPasswordBinding? = null
    private lateinit var auth : FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        auth = FirebaseAuth.getInstance()

        binding?.btnForgotPasswordSubmit?.setOnClickListener {
            resetPassword()
        }

        binding?.btnBack?.setOnClickListener{
            onBackPressed()
            finish()
        }

    }

    private fun validateForm(email: String): Boolean {
        return when {
            TextUtils.isEmpty(email)||!Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding?.etForgotEmailAddress?.error = "Enter valid email address"
                false
            }
            else -> true
        }
    }

    private fun resetPassword(){
        val email = binding?.etForgotEmailAddress?.text.toString()
        if (validateForm(email)){
            showProgressBar()
            auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                if (task.isSuccessful){
                    hideProgressBar()
                    binding?.btnForgotPasswordSubmit?.visibility = View.GONE
                    val dialog = AlertDialog.Builder(this)
                        .setTitle("Forgot Password")
                        .setMessage("Check out your mailbox, you will receive an email containing link where you can reset your password.")
                        .setPositiveButton("OK") { dialogInterface, _ ->
                            dialogInterface.dismiss()
                            val intent = Intent(this@ForgotPassword, LoginScreen::class.java)
                            startActivity(intent)
                            finish()
                        }
                        .create()

                    dialog.show()

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                        ContextCompat.getColor(this, R.color.blue)
                    )
                }
                else{
                    hideProgressBar()
                    showToast(this, "Cannot reset password at the moment")
                }
            }
        }
    }
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}