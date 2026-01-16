package com.example.jsmart

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.jsmart.databinding.ActivityEditProfileScreenBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class EditProfileScreen : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private lateinit var binding: ActivityEditProfileScreenBinding
    private var userListener: ListenerRegistration? = null

    private val originalValues = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {

        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )


        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.modeSwitch.isChecked = isDarkMode



        binding.modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            binding.modeSwitch.postDelayed({
                recreate()
            }, 200)
        }

        setupUI()
        loadUserData()
        setupSaveButton()
    }

    private fun setupUI() {
        val user = auth.currentUser ?: return


        val isGoogleUser = user.providerData.any { it.providerId == "google.com" }

        if (isGoogleUser) {
            binding.etEmail.isEnabled = false
            binding.etEmail.setTextColor(ContextCompat.getColor(this, R.color.gray))
            binding.changePassword.isEnabled = false
            binding.changePassword.alpha = 0.5f
        }

        binding.btnBack.setOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        binding.changePassword.setOnClickListener {
            if (isGoogleUser) {
                Toast.makeText(this, "Google users cannot change password.", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, ChangePassword::class.java))
                finish()
            }
        }

        binding.btnSave.isEnabled = false
        binding.btnSave.alpha = 0.5f

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val changed =
                    binding.etFirstName.text.toString() != originalValues["name"] ||
                            binding.etLastName.text.toString() != originalValues["lastName"] ||
                            binding.etEmail.text.toString() != originalValues["email"]

                binding.btnSave.isEnabled = if (changed) true else false
                binding.btnSave.alpha = if (changed) 1.0f else 0.5f
            }
        }

        binding.etFirstName.addTextChangedListener(watcher)
        binding.etLastName.addTextChangedListener(watcher)
        binding.etEmail.addTextChangedListener(watcher)
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {

            val user = auth.currentUser ?: return@setOnClickListener

            val newFirst = binding.etFirstName.text.toString().trim()
            val newLast = binding.etLastName.text.toString().trim()
            val newEmail = binding.etEmail.text.toString().trim()
            val oldEmail = originalValues["email"] ?: ""

            val emailChanged = newEmail != oldEmail
            val nameChanged =
                newFirst != originalValues["name"] || newLast != originalValues["lastName"]

            val isGoogleUser = user.providerData.any { it.providerId == "google.com" }


            if (emailChanged && isGoogleUser) {
                Toast.makeText(
                    this,
                    "Email cannot be changed for Google Sign-In accounts.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            if (emailChanged) {
                promptForReauthentication(newEmail, newFirst, newLast)
            } else if (nameChanged) {
                updateNameOnly(newFirst, newLast)
            }
        }
    }

    private fun promptForReauthentication(newEmail: String, first: String, last: String) {
        val user = auth.currentUser ?: return

        val passwordInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter current password"
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Security Verification")
            .setView(passwordInput)
            .setPositiveButton("Confirm", null)
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .create()

        dialog.show()


        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.blue)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.gray)
        )


        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = passwordInput.text.toString().trim()
            if (password.isEmpty()) {
                Toast.makeText(this, "Password required.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val credential = EmailAuthProvider.getCredential(user.email!!, password)

            user.reauthenticate(credential)
                .addOnSuccessListener {
                    user.verifyBeforeUpdateEmail(newEmail)
                        .addOnSuccessListener {
                            val updates = mapOf(
                                "name" to first,
                                "lastName" to last,
                                "email" to newEmail
                            )

                            db.collection("users").document(user.uid)
                                .update(updates)
                                .addOnSuccessListener {
                                    dialog.dismiss()
                                    showSignOutDialog(newEmail)
                                }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                e.message ?: "Email update failed.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Incorrect password.", Toast.LENGTH_SHORT).show()
                }
        }
    }



    private fun updateNameOnly(first: String, last: String) {
        val user = auth.currentUser ?: return

        db.collection("users").document(user.uid)
            .update("name", first, "lastName", last)
            .addOnSuccessListener {

                val dialog = AlertDialog.Builder(this)
                    .setTitle("Profile Updated")
                    .setMessage("Your name has been updated successfully!")
                    .setPositiveButton("OK") { dialogInterface, _ ->
                        dialogInterface.dismiss()
                        goBack()
                    }
                    .create()

                dialog.show()


                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                    ContextCompat.getColor(this, R.color.blue)
                )
            }
            .addOnFailureListener {

                val dialog = AlertDialog.Builder(this)
                    .setTitle("Update Failed")
                    .setMessage("Failed to update profile. Please try again.")
                    .setPositiveButton("OK") { dialogInterface, _ ->
                        dialogInterface.dismiss()
                    }
                    .create()

                dialog.show()


                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                    ContextCompat.getColor(this, R.color.gray)
                )
            }
    }


    private fun showSignOutDialog(email: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Email Verification Required")
            .setMessage("A verification email was sent to $email. Please verify and sign in again.")
            .setPositiveButton("OK", null) // set listener later
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .create()

        dialog.show()


        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.blue)
        )


        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.gray)
        )


        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginScreen::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            dialog.dismiss()
        }
    }


    private fun loadUserData() {
        val user = auth.currentUser ?: return

        userListener = db.collection("users").document(user.uid)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    originalValues["name"] = doc.getString("name") ?: ""
                    originalValues["lastName"] = doc.getString("lastName") ?: ""
                    originalValues["email"] = doc.getString("email") ?: ""

                    binding.etFirstName.setText(originalValues["name"])
                    binding.etLastName.setText(originalValues["lastName"])
                    binding.etEmail.setText(originalValues["email"])
                }
            }
    }

    private fun goBack() {
        startActivity(Intent(this, ProfileScreen::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
    }
}
