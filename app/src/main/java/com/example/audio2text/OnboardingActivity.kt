package com.example.audio2text

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity(), OnboardingPageChangeListener {
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = OnboardingAdapter(this)

        // Récupérez la position enregistrée
        val preferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
        val lastPosition = preferences.getInt("LastPosition", 0)

        // Définissez la position enregistrée comme position actuelle
        viewPager.currentItem = lastPosition

        // Enregistrez la position actuelle chaque fois que l'utilisateur change de page
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                preferences.edit().putInt("LastPosition", position).apply()
            }
        })
    }

    override fun moveToPage(position: Int) {
        viewPager.currentItem = position
    }
}