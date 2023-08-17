package com.example.audio2text

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class TutorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.didacticiel)

        // Ajoutez le fragment initial si aucun fragment n'est déjà ajouté
        if (savedInstanceState == null) {
            val fragment = WelcomeFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.container, fragment)
                .commit()
        }
    }
}
