package com.example.jsmart

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.ViewFlipper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.jsmart.databinding.ActivityDefaultScreenBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class DefaultScreen : AppCompatActivity() {
    private var binding: ActivityDefaultScreenBinding? = null
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var userListener: ListenerRegistration? = null
    override fun onCreate(savedInstanceState: Bundle?) {

        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivityDefaultScreenBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        auth = FirebaseAuth.getInstance()

        binding?.homeButton?.setOnClickListener {
            val homeIntent = Intent(this,MainActivity::class.java)
            startActivity(homeIntent)
            finish()
        }
        binding?.btnSignOut?.setOnClickListener {
            showSignOutConfirmation()
        }
        binding?.btnProfile?.setOnClickListener {
            val profileIntent = Intent(this,ProfileScreen::class.java)
            startActivity(profileIntent)
            finish()
        }
        binding?.btnTutorial?.setOnClickListener{
            showTutorialDialog()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })



    }
    private fun showSignOutConfirmation() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Yes") { dialogInterface, _ ->
                signOutUser()
                dialogInterface.dismiss()
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this, R.color.blue))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, R.color.gray))


    }

    private fun signOutUser() {
        userListener?.remove()

        val firebaseUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (firebaseUserId != null) {
            val sharedPreferences = getSharedPreferences("QuizPrefs_$firebaseUserId", Context.MODE_PRIVATE)
            sharedPreferences.edit().clear().apply()
        }

        auth.signOut()

        val intent = Intent(this, LoginScreen::class.java)
        startActivity(intent)
        finish()
    }

    private fun showTutorialDialog() {

        val dialogView = layoutInflater.inflate(R.layout.dialog_tutorial, null)
        val viewFlipper = dialogView.findViewById<ViewFlipper>(R.id.viewFlipper)
        val btnNext = dialogView.findViewById<ImageButton>(R.id.btnNext)
        val btnPrev = dialogView.findViewById<ImageButton>(R.id.btnPrev)
        val btnGotIt = dialogView.findViewById<Button>(R.id.btnGotIt)


        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()


        val animNextIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
        val animNextOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)

        val animPrevIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
        val animPrevOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_right)

        btnNext.setOnClickListener {
            if (viewFlipper.displayedChild < viewFlipper.childCount - 1) {

                viewFlipper.inAnimation = animNextIn
                viewFlipper.outAnimation = animNextOut


                viewFlipper.showNext()
            }
        }

        btnPrev.setOnClickListener {
            if (viewFlipper.displayedChild > 0) {

                viewFlipper.inAnimation = animPrevIn
                viewFlipper.outAnimation = animPrevOut

                viewFlipper.showPrevious()
            }
        }

        btnGotIt.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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