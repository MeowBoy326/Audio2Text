package com.example.audio2text

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int {
        return 6 // Nombre de pages dans le didacticiel
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> WelcomeFragment() // Accueil
            1 -> PermissionExplanationFragment() // Explication et demande de permissions
            2 -> NotificationExplanationFragment() // Explication et activation des notifications
            3 -> ModelSelectionFragment() // Sélection et téléchargement du modèle
            4 -> DictionaryFragment()
            5 -> SettingsFragment()
            else -> throw IllegalArgumentException("Position invalide")
        }
    }
}
