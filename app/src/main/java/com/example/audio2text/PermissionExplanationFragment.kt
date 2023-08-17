package com.example.audio2text

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.Fragment

class PermissionExplanationFragment : Fragment() {

    private val REQUEST_CODE = 1
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_permission_explanation, container, false)
    }



    // check had permission
    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 12
            checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.all { it.value }
            val button = view?.findViewById<Button>(R.id.requestPermissionButton)
            val nextArrow = view?.findViewById<ImageView>(R.id.nextArrow)

            if (allPermissionsGranted && button != null && nextArrow != null) {
                // Charger l'animation de translation
                val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.transition_animation)

                // Appliquer l'animation au bouton et le faire disparaître à la fin
                button.visibility = View.INVISIBLE

                // Positionner la flèche au même endroit que le bouton, puis l'animer vers la droite
                nextArrow.visibility = View.VISIBLE
                nextArrow.startAnimation(animation)

                // Définir un nouveau OnClickListener pour passer au fragment suivant
                nextArrow.setOnClickListener {
                    val nextFragment = NotificationExplanationFragment()
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.container, nextFragment)
                        .addToBackStack(null)
                        .commit()
                }
            } else {
                // Faire trembler le bouton ou toute autre action pour informer l'utilisateur
                button?.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.shake))
            }
        }

    private fun requestPermission() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13
            if (checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO)
            }

            if (checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.requestPermissionButton).setOnClickListener {
            // Demandez les permissions ici
            requestPermission()
        }
    }
}