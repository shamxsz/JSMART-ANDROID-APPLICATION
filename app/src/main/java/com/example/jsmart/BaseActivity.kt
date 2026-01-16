package com.example.jsmart

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    private lateinit var pb: Dialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun showProgressBar() {
        pb = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(this).inflate(R.layout.progress_bar, null)
        pb.window?.setBackgroundDrawableResource(android.R.color.transparent)
        pb.setContentView(view)
        pb.setCancelable(false)
        pb.show()
    }

    fun hideProgressBar() {
        if (this::pb.isInitialized && pb.isShowing) {
            pb.dismiss()
        }
    }

    fun showToast(activity: Activity, msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }
}
