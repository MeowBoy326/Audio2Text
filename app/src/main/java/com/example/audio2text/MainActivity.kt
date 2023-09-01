package com.example.audio2text

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation.findNavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    lateinit var homeViewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //val sharedPref = applicationContext.getSharedPreferences("transcription", Context.MODE_PRIVATE)
        /*with(sharedPref.edit()) {
            clear()
            apply()
        }*/
        setContentView(R.layout.activity_main)

        val preferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
        val lastPostion = preferences.getInt("LastPosition", 0)
        Log.d("MainActivity", "Position in Adapter: $lastPostion")
        if (lastPostion >= 0) {
            // Rediriger vers OnboardingActivity
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController

            val toolbar: Toolbar = findViewById(R.id.toolbar)
            //toolbar.inflateMenu(R.menu.menu_toolbar)
            setSupportActionBar(toolbar)

            // Configurer le tiroir
            drawerLayout = findViewById(R.id.drawer_layout)
            navView = findViewById(R.id.nav_view)

            homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]


            toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.copy_action -> {
                        // Naviguez vers le fragment des paramètres
                        val textToCopy = homeViewModel.transcription.value ?: ""
                        val clipboard =
                            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Transcribed Text", textToCopy)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Texte copié", Toast.LENGTH_SHORT).show()
                        true
                    }

                    else -> false
                }
            }

            // Configurer AppBarConfiguration
            val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.homeFragment /* Autres IDs de destinations de niveau supérieur */
                ), drawerLayout
            )

            supportActionBar?.setDisplayHomeAsUpEnabled(true)

            // Configurer le NavController avec le menu
            navView.setupWithNavController(navController)

            toolbar.setupWithNavController(navController, appBarConfiguration)

            // Configure le NavController avec la barre d'outils et AppBarConfiguration
            setupActionBarWithNavController(navController, appBarConfiguration)

            homeViewModel.isTranscriptionTextEnabled.observe(this) { isEnabled ->
                val item = toolbar.menu.findItem(R.id.copy_action)
                item?.isEnabled = isEnabled
                //invalidateOptionsMenu()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return NavigationUI.navigateUp(navController, drawerLayout) || super.onSupportNavigateUp()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val viewRect = Rect()
        navView.getGlobalVisibleRect(viewRect)

        if (!viewRect.contains(ev.rawX.toInt(), ev.rawY.toInt()) && drawerLayout.isDrawerOpen(
                GravityCompat.START
            )
        ) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        return super.dispatchTouchEvent(ev)
    }
}
