package com.example.audio2text

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PermissionExplanationFragment : Fragment() {

    private val REQUEST_CODE = 1

    lateinit var button: Button
    lateinit var nextArrow: FloatingActionButton
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_permission_explanation, container, false)
        button = view.findViewById(R.id.requestPermissionButton)

        nextArrow = view.findViewById(R.id.nextArrow1)
        return view
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.all { it.value }

            if (allPermissionsGranted) {
                val permisssionText = view?.findViewById<TextView>(R.id.text_permissions)
                permisssionText?.text = "Permissions accordées !"
                // Charger l'animation de translation
                val animation =
                    AnimationUtils.loadAnimation(requireContext().applicationContext, R.anim.transition_animation)

                // Appliquer l'animation au bouton et le faire disparaître à la fin
                button.visibility = View.INVISIBLE

                // Positionner la flèche au même endroit que le bouton, puis l'animer vers la droite
                nextArrow.visibility = View.VISIBLE
                nextArrow.startAnimation(animation)

                // Définir un nouveau OnClickListener pour passer au fragment suivant
                nextArrow.setOnClickListener {
                    // Appellez moveToPage() sur l'activité pour changer de page
                    (activity as? OnboardingPageChangeListener)?.moveToPage(2)
                }
            } else {
                // Faire trembler le bouton ou toute autre action pour informer l'utilisateur
                button.startAnimation(AnimationUtils.loadAnimation(requireContext().applicationContext, R.anim.shake))
            }
        }

    private fun handlePermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(
                    requireContext().applicationContext,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO)
            }

            if (checkSelfPermission(
                    requireContext().applicationContext,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        }
        else {
            if (checkSelfPermission(
                    requireContext().applicationContext,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            // Montrer le bouton ou demander directement les permissions
            button.setOnClickListener {
                requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
            }
        } else {
            // Toutes les permissions sont accordées, cacher le bouton ou continuer
            // avec la logique suivante de votre application
            val permisssionText = view?.findViewById<TextView>(R.id.text_permissions)
            permisssionText?.text = "Permissions accordées !"
            // Charger l'animation de translation
            val animation =
                AnimationUtils.loadAnimation(requireContext().applicationContext, R.anim.transition_animation)

            // Appliquer l'animation au bouton et le faire disparaître à la fin
            button.visibility = View.INVISIBLE

            // Positionner la flèche au même endroit que le bouton, puis l'animer vers la droite
            nextArrow.visibility = View.VISIBLE
            nextArrow.startAnimation(animation)

            // Définir un nouveau OnClickListener pour passer au fragment suivant
            nextArrow.setOnClickListener {
                // Appellez moveToPage() sur l'activité pour changer de page
                (activity as? OnboardingPageChangeListener)?.moveToPage(2)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Demandez les permissions ici
        handlePermissions()
    }
}