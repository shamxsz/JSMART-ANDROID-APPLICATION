package com.example.jsmart


import android.content.Context
import android.os.Bundle
import android.view.WindowInsets
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.example.jsmart.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {

        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        val insetsController: WindowInsetsControllerCompat = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.itemIconTintList = ContextCompat.getColorStateList(this, R.color.nav_bar_icon_light)
        bottomNavigationView.itemTextColor = ContextCompat.getColorStateList(this, R.color.nav_bar_icon_light)



        replaceFragment(HomeFragment())
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> replaceFragment(HomeFragment())
                R.id.module -> replaceFragment(ModuleFragment())
                R.id.quiz -> replaceFragment(QuizFragment())
                R.id.exercise -> replaceFragment(ExerciseFragment())
                else -> return@setOnItemSelectedListener false
            }
            true
        }

    }



    private fun replaceFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager


        if (!fragmentManager.isStateSaved && !fragmentManager.isDestroyed) {
            fragmentManager.beginTransaction()
                .replace(R.id.frame_layout, fragment)
                .commitAllowingStateLoss()
        }
    }


}
