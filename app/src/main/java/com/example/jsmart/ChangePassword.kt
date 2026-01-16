package com.example.jsmart

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.jsmart.databinding.ActivityChangePasswordBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View


class ChangePassword : AppCompatActivity() {
    private lateinit var binding: ActivityChangePasswordBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.etCurrentPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.etNewPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.etConfirmPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                binding.etCurrentPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.etNewPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.etCurrentPassword.setSelection(binding.etCurrentPassword.text.length)
            binding.etNewPassword.setSelection(binding.etNewPassword.text.length)
            binding.etConfirmPassword.setSelection(binding.etConfirmPassword.text.length)

        }

        binding.etNewPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.passwordChecklist.visibility = View.VISIBLE
            }else
                binding.passwordChecklist.visibility = View.GONE
        }

        binding.etNewPassword.addTextChangedListener(object : TextWatcher {
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


        binding.btnChangePass.setOnClickListener {
            val dialog = AlertDialog.Builder(this)
                .setTitle("Confirm Password Change")
                .setMessage("Do you really want to change your password?")
                .setPositiveButton("Yes") { _, _ ->
                    changePassword()
                }
                .setNegativeButton("No", null)
                .create()

                dialog.show()

                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this, R.color.blue))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, R.color.gray))
        }


        binding.btnBack.setOnClickListener{
            onBackPressed()
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@ChangePassword, EditProfileScreen::class.java)
                startActivity(intent)
                finish()
            }
        })

    }

    private fun updateChecklist(
        length: Boolean,
        upper: Boolean,
        lower: Boolean,
        digit: Boolean,
        special: Boolean
    ) {
        updateItem(binding.checkLength, length)
        updateItem(binding.checkUpper, upper)
        updateItem(binding.checkLower, lower)
        updateItem(binding.checkNumber, digit)
        updateItem(binding.checkSpecial, special)
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



    private fun validateForm(currentPassword: String, newPassword: String, confirmPassword: String): Boolean {
        var isValid = true

        if (TextUtils.isEmpty(currentPassword)) {
            binding.etCurrentPassword.error = "Enter current password"
            isValid = false
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            binding.etConfirmPassword.error = "Please confirm your password"
            isValid = false
        }
        if (newPassword != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            isValid = false
        }
        if (TextUtils.isEmpty(newPassword)) {
            binding.etNewPassword.error = "Enter new password"
            isValid = false

        }
        return isValid
    }

    private fun changePassword() {
        val currentPassword = binding.etCurrentPassword.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (!validateForm(currentPassword, newPassword, confirmPassword)) {
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email

        if (user != null && !email.isNullOrEmpty()) {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)

            user.reauthenticate(credential)
                .addOnSuccessListener {

                    val newPasswordHash = hashPassword(newPassword)
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val userDoc = db.collection("users").document(user.uid)

                    userDoc.get().addOnSuccessListener { document ->
                        val history =
                            document.get("passwordHistory") as? List<String> ?: emptyList()

                        if (history.contains(newPasswordHash)) {
                            val dialog = AlertDialog.Builder(this)
                                .setTitle("Warning")
                                .setMessage("You cannot use your previous password.")
                                .setPositiveButton("OK"){ _, _ ->
                                    binding.etNewPassword.text.clear()
                                    binding.etConfirmPassword.text.clear()
                                }
                            .create()
                            dialog.show()
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this,R.color.blue))
                            return@addOnSuccessListener
                        }

                        user.updatePassword(newPassword)
                            .addOnSuccessListener {

                                val updatedHistory = (history + newPasswordHash).takeLast(5)
                                userDoc.update("passwordHistory", updatedHistory)

                                binding.etCurrentPassword.text?.clear()
                                binding.etNewPassword.text?.clear()
                                binding.etConfirmPassword.text?.clear()

                                val dialog = AlertDialog.Builder(this)
                                    .setTitle("Change Password Successfully")
                                    .setMessage("You changed your password successfully!")
                                    .setPositiveButton("OK") { _, _ ->
                                        startActivity(
                                            Intent(this@ChangePassword, EditProfileScreen::class.java)
                                        )
                                        finish()
                                    }
                                    .create()
                                    dialog.show()

                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this,R.color.blue))
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Reauthentication failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}