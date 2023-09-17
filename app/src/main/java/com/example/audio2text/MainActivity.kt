package com.example.audio2text

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.material.navigation.NavigationView
import java.util.concurrent.atomic.AtomicBoolean


class MainActivity : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences
    private lateinit var navController: NavController
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var popupWindow: PopupWindow
    private var editAction: TextView? = null
    private var copyAction: TextView? = null
    private var exportAction: TextView? = null
    private var correctAction: TextView? = null
    //private var isMobileAdsInitializeCalled = AtomicBoolean(false)
    /*private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        // À implémenter dans une section ultérieure.
    }

    private var billingClient = BillingClient.newBuilder(applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()*/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //val sharedPref = applicationContext.getSharedPreferences("transcription", Context.MODE_PRIVATE)
        /*with(sharedPref.edit()) {
            clear()
            apply()
        }*/
        setContentView(R.layout.activity_main)

        preferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
        val lastPosition = preferences.getInt("LastPosition", 0)

        val intent = intent
        val action = intent.action
        val type = intent.type

        Log.d("MainActivity", "Position in Adapter: $lastPosition")
        if (lastPosition >= 0) {
            // Rediriger vers OnboardingActivity
            val intentToOnboard = Intent(this, OnboardingActivity::class.java)
            startActivity(intentToOnboard)
            finish()
        } else {

            navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navController = navHostFragment.navController


            val inflater: LayoutInflater =
                getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.dialog_layout, LinearLayout(this), false)

            val width = LinearLayout.LayoutParams.WRAP_CONTENT
            val height = LinearLayout.LayoutParams.WRAP_CONTENT
            popupWindow = PopupWindow(popupView, width, height, true)

            copyAction = popupView.findViewById(R.id.copy_action)
            editAction = popupView.findViewById(R.id.edit_action)
            correctAction = popupView.findViewById(R.id.correct_action)
            exportAction = popupView.findViewById(R.id.export_action)

            // Ajoutez des OnClickListener pour chaque action
            copyAction?.setOnClickListener {
                // Naviguez vers le fragment des paramètres
                val textToCopy = HomeViewModelHolder.viewModel.transcription.value ?: ""
                Log.d("MainActivity", "textToCopy: $textToCopy")
                val clipboard =
                    getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Transcribed Text", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Texte copié", Toast.LENGTH_SHORT).show()
                popupWindow.dismiss()
            }

            editAction?.setOnClickListener {
                // Votre logique pour "Editer"
                if (HomeViewModelHolder.viewModel.isEditingTranscriptionText.value == true) {
                    HomeViewModelHolder.viewModel.isEditingTranscriptionText.postValue(false)
                    //item.title = "Vérouiller"
                } else {
                    HomeViewModelHolder.viewModel.isEditingTranscriptionText.postValue(true)
                    //item.title = "Editer"
                }
                popupWindow.dismiss()
            }

            correctAction?.setOnClickListener {
                // Votre logique pour "Corriger"
                if (SpellCheckerSingleton.isSpellCheckerReady.value == true) {
                    HomeViewModelHolder.viewModel.isSpellAlreadyRequested.postValue(true)
                    HomeViewModelHolder.viewModel.isRequestSpellingSuggestions.postValue(true)
                } else {
                    Toast.makeText(this, "La correction n'est pas encore disponible. Merci de patienter...", Toast.LENGTH_SHORT).show()
                }
                popupWindow.dismiss()
            }

            exportAction?.setOnClickListener {
                // Votre logique pour "Exporter"
                exportTranscription()
                popupWindow.dismiss()
            }

            val toolbar: Toolbar = findViewById(R.id.toolbar)
            //toolbar.inflateMenu(R.menu.menu_toolbar)
            setSupportActionBar(toolbar)

            // Configurer le tiroir
            drawerLayout = findViewById(R.id.drawer_layout)
            navView = findViewById(R.id.nav_view)

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


                    /*billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode ==  BillingClient.BillingResponseCode.OK) {
                        // Le BillingClient est prêt. Vous pouvez interroger les achats ici.
                    }
                }
                override fun onBillingServiceDisconnected() {
                    // Logique de reconnexion
                }
            })

            val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf("votre_id_de_produit"))
                .build()

            billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
                // Traitez les produits disponibles ici
            }*/

            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR,"307EA1C1A01B77AA438B824C05117839")).build()
            )


            MobileAds.initialize(this) {
                    initializationStatus -> Log.d("AdMob", "Initialization complete: ${initializationStatus.adapterStatusMap}")
            }

            val isFullySuccessful = preferences.getBoolean("isFullySuccessful", false)
            val isFullyStopped = preferences.getBoolean("isFullyStopped", false)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (isFullySuccessful == true) {
                //preferences.edit()?.putBoolean("isFullySuccessful", false)?.apply()
                //preferences.edit()?.putBoolean("isInitialized", true)?.apply()
                notificationManager.cancel(43)
            } else if (isFullyStopped == true) {
                //preferences.edit()?.putBoolean("isFullyStopped", false)?.apply()
                //preferences.edit()?.putBoolean("isInitialized", true)?.apply()
                notificationManager.cancel(43)
            }

            navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.homeFragment -> {
                        // Afficher la barre d'outils et ses éléments
                        invalidateOptionsMenu()  // Cela va déclencher onCreateOptionsMenu()
                    }

                    else -> {
                        // Masquer ou modifier les éléments de la barre d'outils
                        invalidateOptionsMenu()  // Cela va déclencher onCreateOptionsMenu()
                    }
                }
            }

            SpellCheckerSingleton.isSpellCheckerReady.observe(
                this,
                object : Observer<Boolean> {
                    override fun onChanged(value: Boolean) {
                        if (value == true) {
                            Log.d("MainActivity", "SpellCheckerReady")
                            HomeViewModelHolder.viewModel.pendingSegments.forEach { segment ->
                                SpellCheckerSingleton.spellCheckerReadyListener?.onSpellCheckerReady(segment)
                            }
                            HomeViewModelHolder.viewModel.pendingSegments.clear()
                            HomeViewModelHolder.viewModel.isNowReadyToCorrect.postValue(true)
                        } else {
                            HomeViewModelHolder.viewModel.misspelledWords.clear()
                            HomeViewModelHolder.viewModel.isRequestSpellingSuggestions.postValue(false)
                        }
                    }
                })

            HomeViewModelHolder.viewModel.isNowReadyToCorrect.observe(
                this,
                object : Observer<Boolean> {
                    override fun onChanged(value: Boolean) {
                        if (HomeViewModelHolder.viewModel.isEditableTranscriptionText.value == true && HomeViewModelHolder.viewModel.isEditableTranscriptionText.value == false) {
                            correctAction?.isEnabled = value
                        }
                        //toolbar.menu.findItem(R.id.correct_action)?.isEnabled = value
                        popupWindow.update()
                    }
                })

            HomeViewModelHolder.viewModel.isEditableTranscriptionText.observe(
                this,
                object : Observer<Boolean> {
                    override fun onChanged(value: Boolean) {
                        editAction?.isEnabled = value
                        copyAction?.isEnabled = value
                        exportAction?.isEnabled = value
                        HomeViewModelHolder.viewModel.isEditingTranscriptionText.postValue(value)
                        popupWindow.update()
                    }
                })

            HomeViewModelHolder.viewModel.isEditingTranscriptionText.observe(
                this, object : Observer<Boolean> {
                    override fun onChanged(value: Boolean) {
                        val newTitle = if (value) "Verrouiller" else "Editer"
                        editAction?.text = newTitle
                        if (HomeViewModelHolder.viewModel.isEditableTranscriptionText.value == true) {
                            correctAction?.isEnabled = !value
                        }
                    }
                }
            )
        }

        val uri = intent.data
        Log.d("MainActivity", "uri: $uri")
        if (lastPosition >= 0 && uri != null && uri.scheme == "content") {
            if (Intent.ACTION_VIEW == action && type != null) {
                if (type.startsWith("audio/") || type.startsWith("video/")) {
                    val fileUri: Uri? = intent.data
                    // Ajoutez l'URI à un Bundle ou à un ViewModel partagé
                    val bundle = Bundle()
                    bundle.putString("fileUri", fileUri.toString())
                    navHostFragment.arguments = bundle
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_options -> {
                showDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDialog() {
        // Obtenez la vue ancrage pour positionner le PopupWindow
        val anchor: View =
            findViewById(R.id.action_options)  // l'ID de l'élément de menu dans la Toolbar

        // Affichez le PopupWindow en bas de la vue ancrage
        popupWindow.showAsDropDown(anchor, 0, 7, Gravity.END)
    }

    private fun exportTranscription() {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                HomeViewModelHolder.viewModel.transcription.value?.substringAfter("\n\n")
            )
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Partager via"))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        when (navController.currentDestination?.id) {
            R.id.homeFragment -> inflater.inflate(R.menu.menu_toolbar, menu)
            else -> menu.clear()
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
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
