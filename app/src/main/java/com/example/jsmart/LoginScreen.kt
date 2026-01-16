package com.example.jsmart

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.Patterns
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.jsmart.databinding.ActivityLoginScreenBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore


class LoginScreen : BaseActivity() {
    private var binding: ActivityLoginScreenBinding? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {

        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        installSplashScreen()
        binding = ActivityLoginScreenBinding.inflate(layoutInflater)
        setContentView(binding?.root)


        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding?.tvRegister?.setOnClickListener {
            startActivity(Intent(this, SignUpScreen::class.java))
            finish()
        }

        binding?.tvForgotPassword?.setOnClickListener {
            startActivity(Intent(this, ForgotPassword::class.java))
        }

        binding?.btnLogin?.setOnClickListener {
            loginUser()
        }

        binding?.btnSignInWithGoogle?.setOnClickListener {
            signInWithGoogle()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })

        binding?.cbShowPassword?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding?.etPassword?.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                binding?.etPassword?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding?.etPassword?.setSelection(binding?.etPassword!!.text.length)
        }
        val auth = Firebase.auth
        if (auth.currentUser != null){
            startActivity(Intent(this,DefaultScreen::class.java))
            finish()
        }
    }

    private fun loginUser() {
        val email = binding?.etEmailAddress?.text.toString()
        val password = binding?.etPassword?.text.toString()
        if (validateForm(email, password)) {
            showProgressBar()
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        if (firebaseUser != null && firebaseUser.isEmailVerified) {
                            showToast(this, "Login successful!")
                            startActivity(Intent(this, DefaultScreen::class.java))
                            hideProgressBar()
                            finish()
                        } else {
                            showToast(this, "Please verify your email before logging in.")
                            auth.signOut()
                            hideProgressBar()
                        }
                    } else {
                        val exception = task.exception
                        if (exception is FirebaseAuthInvalidCredentialsException) {
                            showToast(this, "Wrong email or password. Please check and try again.")
                            hideProgressBar()
                        } else {
                            hideProgressBar()
                        }
                    }
                }
        }
    }



    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleResults(task)
        }
    }

    private fun handleResults(task: Task<GoogleSignInAccount>) {
        if (task.isSuccessful) {
            val account: GoogleSignInAccount? = task.result
            account?.let {
                firebaseAuthWithGoogle(it)
            }
        } else {
            showToast(this, "Google Login Failed")
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        showProgressBar()
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            hideProgressBar()
            if (task.isSuccessful) {
                val firebaseUser = auth.currentUser
                firebaseUser?.let {
                    val userRef = db.collection("users").document(it.uid)
                    userRef.get().addOnSuccessListener { document ->
                        if (!document.exists()) {
                            val user = hashMapOf(
                                "uid" to it.uid,
                                "name" to (it.displayName ?: "Guest"),
                                "email" to (it.email ?: ""),
                                "photoUrl" to (it.photoUrl?.toString() ?: "")
                            )
                            userRef.set(user)
                        }
                    }
                }
                startActivity(Intent(this, DefaultScreen::class.java))
                finish()
            } else {
                showToast(this, "Can't login currently. Try again later")
            }
        }
    }

    private fun validateForm(email: String, password: String): Boolean {
        var isValid = true


        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding?.etEmailAddress?.error = "Enter a valid email address"
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



}

