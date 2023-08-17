package com.example.audio2text

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.fragment.app.Fragment

class NotificationExplanationFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notification_explanation, container, false)
    }

    private fun checkNotifications() {
        val notificationManager = activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val button = view?.findViewById<Button>(R.id.enableNotificationsButton)
        val nextArrow = view?.findViewById<ImageView>(R.id.nextArrow)

        if (notificationManager.areNotificationsEnabled()) {
            // Charger l'animation de translation
            val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.transition_animation)

            button?.visibility = View.INVISIBLE

            // Positionner la flèche au même endroit que le bouton, puis l'animer vers la droite
            nextArrow?.visibility = View.VISIBLE
            nextArrow?.startAnimation(animation)

            // Définir un nouveau OnClickListener pour passer au fragment suivant
            nextArrow?.setOnClickListener {
                val nextFragment = ModelSelectionFragment()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, nextFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val button = view.findViewById<Button>(R.id.enableNotificationsButton)
        button.setOnClickListener {
            val notificationManager = activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.areNotificationsEnabled()) {
                AlertDialog.Builder(activity)
                    .setTitle("Les notifications sont désactivées")
                    .setMessage("Voulez-vous activer les notifications ?")
                    .setPositiveButton("Oui") { _, _ ->
                        val intent = Intent().apply {
                            action = "android.settings.APP_NOTIFICATION_SETTINGS"
                            putExtra("app_package", activity?.packageName)
                            putExtra("app_uid", activity?.applicationInfo?.uid)
                            putExtra("android.provider.extra.APP_PACKAGE", activity?.packageName)
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Non", null)
                    .show()
            } else {
                // Si les notifications sont activées, vérifiez leur état
                checkNotifications()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotifications()
    }
}