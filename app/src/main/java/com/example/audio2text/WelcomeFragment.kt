package com.example.audio2text

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class WelcomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Animation pour faire tomber le logo
        val logoImageView: ImageView = view.findViewById(R.id.logoImageView)
        val color = ContextCompat.getColor(requireContext(), R.color.imageViewColor)
        logoImageView.setColorFilter(color)
        val animation = ObjectAnimator.ofFloat(logoImageView, "translationY", -1000f, 0f)
        animation.duration = 500 // Durée de l'animation en millisecondes
        animation.start()

        // Gestionnaire de clic pour le bouton Démarrer
        view.findViewById<Button>(R.id.startButton).setOnClickListener {
            // Appellez moveToPage() sur l'activité pour changer de page
            (activity as? OnboardingPageChangeListener)?.moveToPage(1)
        }
    }
}