package com.example.audio2text

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton


class NotificationExplanationFragment : Fragment() {
    lateinit var button : Button
    lateinit var nextArrow : FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notification_explanation, container, false)
        button =  view.findViewById(R.id.enableNotificationsButton)

        nextArrow = view.findViewById(R.id.nextArrow2)
        return view
    }

    private fun checkNotifications() {
        val notificationManager = activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.areNotificationsEnabled()) {
            // Charger l'animation de translation
            val animation = AnimationUtils.loadAnimation(requireContext().applicationContext, R.anim.transition_animation)
            val textNotif = view?.findViewById<TextView>(R.id.text_notif)
            textNotif?.text = "Notifications activées !"
            button.visibility = View.INVISIBLE

            // Positionner la flèche au même endroit que le bouton, puis l'animer vers la droite
            nextArrow.visibility = View.VISIBLE
            nextArrow.startAnimation(animation)

            // Définir un nouveau OnClickListener pour passer au fragment suivant
            nextArrow.setOnClickListener {
                // Appellez moveToPage() sur l'activité pour changer de page
                (activity as? OnboardingPageChangeListener)?.moveToPage(3)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val button = view.findViewById<Button>(R.id.enableNotificationsButton)
        button.setOnClickListener {
            val notificationManager = activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.areNotificationsEnabled()) {
                val builder = AlertDialog.Builder(activity)
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
                    //.show()

                val alert = builder.create()
                alert.show()
                val nbutton: Button = alert.getButton(DialogInterface.BUTTON_NEGATIVE)
                nbutton.setTextColor(ContextCompat.getColor(requireContext().applicationContext,R.color.menu_text_color))
                val pbutton: Button = alert.getButton(DialogInterface.BUTTON_POSITIVE)
                pbutton.setTextColor(ContextCompat.getColor(requireContext().applicationContext,R.color.menu_text_color))
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