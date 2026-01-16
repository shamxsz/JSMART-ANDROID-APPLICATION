package com.example.jsmart

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.jsmart.databinding.ActivitySignUpScreenBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class SignUpScreen : BaseActivity() {
    private var binding: ActivitySignUpScreenBinding? = null
    private lateinit var auth: FirebaseAuth
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivitySignUpScreenBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        auth = Firebase.auth

        binding?.tvLoginPage?.setOnClickListener {
            startActivity(Intent(this, LoginScreen::class.java))
            finish()
        }

        binding?.btnSignUp?.setOnClickListener {
            registerUser()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })


        binding?.cbShowPassword?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding?.etPassword?.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding?.etConfirmPassword?.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                binding?.etPassword?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding?.etConfirmPassword?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

            }
            binding?.etPassword?.setSelection(binding?.etPassword!!.text.length)
            binding?.etConfirmPassword?.setSelection(binding?.etConfirmPassword!!.text.length)
        }

        binding?.etPassword?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding?.passwordChecklist?.visibility = View.VISIBLE
            }else
                binding?.passwordChecklist?.visibility = View.GONE
        }

        binding?.etPassword?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()

                updateChecklist(
                    length = password.length >= 8,
                    upper = password.any { it.isUpperCase() },
                    lower = password.any { it.isLowerCase() },
                    digit = password.any { it.isDigit() },
                    special = password.any { it in "@#\$%^&+=!?*_-+~" }
                )
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

    }

    private fun registerUser() {
        val name = binding?.etName?.text.toString()
        val lastName = binding?.etLastName?.text.toString()
        val email = binding?.etEmailAddress?.text.toString()
        val password = binding?.etPassword?.text.toString()
        val confirmPassword = binding?.etConfirmPassword?.text.toString()

        if (validateForm(name, lastName, email, password, confirmPassword)) {
            showProgressBar()
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        firebaseUser?.let {
                            auth.currentUser?.sendEmailVerification()
                                ?.addOnSuccessListener {
                                    showToast(this, "Verification email sent. Please check your inbox.")
                                }
                                ?.addOnFailureListener { e ->
                                    showToast(this, "Failed to send verification email: ${e.message}")
                                }
                                val passwordHash = hashPassword(password)

                                val user = hashMapOf(
                                    "uid" to it.uid,
                                    "name" to name,
                                    "lastName" to lastName,
                                    "email" to email,
                                    "passwordHistory" to listOf(passwordHash)
                                )

                            db.collection("users").document(it.uid)
                                .set(user)
                                .addOnSuccessListener {
                                    showToast(this, "User registered successfully!")
                                    auth.signOut()
                                    startActivity(Intent(this, LoginScreen::class.java))
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    showToast(this, "Failed to save user data: ${'$'}{e.message}")
                                }
                        }
                    } else {
                        showToast(this, "User registration failed. Try again later.")
                    }
                    hideProgressBar()
                }
        }
    }

    private fun updateChecklist(
        length: Boolean,
        upper: Boolean,
        lower: Boolean,
        digit: Boolean,
        special: Boolean
    ) {
        binding?.let { updateItem(it.checkLength, length) }
        binding?.let { updateItem(it.checkUpper, upper) }
        binding?.let { updateItem(it.checkLower, lower) }
        binding?.let { updateItem(it.checkNumber, digit) }
        binding?.let { updateItem(it.checkSpecial, special) }
    }

    private fun updateItem(textView: android.widget.TextView, isValid: Boolean) {
        if (isValid) {
            textView.setTextColor(Color.parseColor("#2E7D32")) // green
            textView.text = "✓ ${textView.text.toString().substring(2)}"
        } else {
            textView.setTextColor(Color.GRAY)
            textView.text = "• ${textView.text.toString().substring(2)}"
        }
    }


    private fun validateForm(
        name: String,
        lastName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        if (TextUtils.isEmpty(name)) {
            binding?.etName?.error = "Enter First Name"
            isValid = false
        }

        if (TextUtils.isEmpty(lastName)) {
            binding?.etLastName?.error = "Enter Last Name"
            isValid = false
        }

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding?.etEmailAddress?.error = "Enter a valid email address"
            isValid = false
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            binding?.etConfirmPassword?.error = "Please confirm your password"
            isValid = false
        }

        if (password != confirmPassword) {
            binding?.etConfirmPassword?.error = "Passwords do not match"
            isValid = false
        }
        if (TextUtils.isEmpty(password)) {
            binding?.etPassword?.error = "Enter password"
            isValid = false


        }
        return isValid
    }

    private fun showExitDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Exit App?")
            .setMessage("Do you want to close the application?")
            .setPositiveButton("Yes") { dialogInterface, _ ->
                dialogInterface.dismiss()
                finish()
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this, R.color.blue))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, R.color.gray))
    }

    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }


}




