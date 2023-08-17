package com.example.audio2text

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int {
        return 3 // Nombre de pages dans le didacticiel
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PermissionExplanationFragment() // Explication et demande de permissions
            1 -> NotificationExplanationFragment() // Explication et activation des notifications
            2 -> ModelSelectionFragment() // Sélection et téléchargement du modèle
            else -> throw IllegalArgumentException("Position invalide")
        }
    }
}
