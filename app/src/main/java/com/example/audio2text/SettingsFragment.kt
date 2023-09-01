package com.example.audio2text

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale


class SettingsFragment : Fragment() {
    private lateinit var buttonConfirm: Button

    companion object {
        val languages = listOf(
            "Automatique" to "auto",
            "Français" to "fr",
            "Anglais" to "en",
            "Espagnol" to "es",
            "Allemand" to "de",
            "Chinois" to "zh",
            "Russe" to "ru",
            "Japonais" to "ja",
            "Portugais" to "pt",
            "Turque" to "tr",
            "Vietnamien" to "vi",
            "Arabe" to "ar",
            "Indonésien" to "id",
            "Coreen" to "ko",
            "Roumain" to "ro",
            "Italien" to "it",
            "Polonais" to "pl",
            "Grec" to "el",
            "Hindi" to "hi",
            "Bengali" to "bn",
            "Tamil" to "ta",
            "Telugu" to "te",
            "Malayalam" to "ml",
            "Urdu" to "ur",
            "Punjabi" to "pa",
            "Catalan" to "ca",
            "Marathi" to "mr",
            "Bosnian" to "bs",
            "Kannada" to "kn",
            "Sindhi" to "sd",
            "Sinhala" to "si",
            "Malay" to "ms",
            "Burmese" to "my",
            "Tibetan" to "bo",
            "Hausa" to "ha",
            "Yoruba" to "yo",
            "Nepali" to "ne",
            "Pashto" to "ps",
            "Sanskrit" to "sa",
            "Néerlandais" to "nl",
            "Persan" to "fa",
            "Bulgarien" to "bg",
            "Lithuanien" to "lt",
            "Latin" to "la",
            "Maori" to "mi",
            "Welsh" to "cy",
            "Slovak" to "sk",
            "Latvian" to "lv",
            "Serbian" to "sr",
            "Azerbeidjanien" to "az",
            "Slovenian" to "sl",
            "Estonian" to "et",
            "Macedonian" to "mk",
            "Breton" to "br",
            "Basque" to "eu",
            "Icelandic" to "is",
            "Armenian" to "hy",
            "Mongolian" to "mn",
            "Kazakh" to "kk",
            "Albanian" to "sq",
            "Swahili" to "sw",
            "Galician" to "gl",
            "Khmer" to "km",
            "Shona" to "sn",
            "Suédois" to "sv",
            "Finnois" to "fi",
            "Hébreux" to "he",
            "Lao" to "lo",
            "Uzbek" to "uz",
            "Faroese" to "fo",
            "Haitien" to "ht",
            "Ukrainien" to "uk",
            "Nynorsk" to "nn",
            "Maltese" to "mt",
            "Tchèque" to "cs",
            "Danois" to "da",
            "Hongrois" to "hu",
            "Norvégien" to "no",
            "Thai" to "th",
            "Yiddish" to "yi",
            "Lingala" to "ln",
            "Afrikaans" to "af",
            "Occitan" to "oc",
            "Georgien" to "ka",
            "Biélorusse" to "be",
            "Tadjik" to "tg",
            "Gujarati" to "gu",
            "Amharique" to "am",
            "Turkmène" to "tk",
            "Luxembourgeois" to "lb",
            "Tagalog" to "tl",
            "Malgache" to "mg",
            "Assamais" to "as",
            "Tatar" to "tt",
            "Hawaïen" to "haw",
            "Bashkir" to "ba",
            "Javanais" to "jw",
            "Soudanais" to "su",
            "Croate" to "hr",
            "Somalien" to "so",
        )
    }

    val languagesToIgnore = listOf("Aucune" to "") + languages.drop(1)
    var startTimeInSeconds = 0
    var endTimeInSeconds = 0
    var maxTextSize = 1000

    private lateinit var languageSelected: String
    private lateinit var languageToIgnore: String
    private var translate: Boolean = false
    private var speed: Boolean = false
    private var seekBarValue: Int = 5
    private var initialPrompt: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val preferences = requireContext().getSharedPreferences("MyPreferences", AppCompatActivity.MODE_PRIVATE)

        // Initialisation des composants de la vue ici, comme les boutons, les commutateurs, etc.
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            languages.map { it.first } // Utilisez seulement les noms de langue
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val spinner: Spinner = view.findViewById(R.id.spinner_language)
        spinner.adapter = adapter

        val spinnerIgnore: Spinner = view.findViewById(R.id.spinner_ignore_language)

        val adapterIgnore = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languagesToIgnore.map { it.first })
        adapterIgnore.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerIgnore.adapter = adapterIgnore

        spinnerIgnore.isEnabled = false

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                view?.let {
                    languageSelected = languages[position].second
                } ?: run {
                    languageSelected = "auto"
                }

                spinnerIgnore.isEnabled = languageSelected == "auto" // Activer/désactiver selon la sélection
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Gérez le cas où rien n'est sélectionné si nécessaire
                languageSelected = "auto"
            }
        }

        spinnerIgnore.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                view?.let {
                    languageToIgnore = languagesToIgnore[position].second
                } ?: run {
                    languageToIgnore = ""
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Faire quelque chose si rien n'est sélectionné
                languageToIgnore = ""
            }
        }

        // Trouver la référence du SwitchCompat
        val switchTranslate = view.findViewById<SwitchCompat>(R.id.translate_switch)
        val switchSpeed = view.findViewById<SwitchCompat>(R.id.speed_switch)

        switchTranslate.setOnCheckedChangeListener { _, isChecked ->
            translate = isChecked
        }

        switchSpeed.setOnCheckedChangeListener { _, isChecked ->
            speed = isChecked
        }

        val initialPromptEditText: TextInputEditText = view.findViewById(R.id.edittext_initial_prompt)

        val segmentLengthSeekBar: SeekBar = view.findViewById(R.id.segment_length_seekbar)
        val segmentLengthValue: TextView = view.findViewById(R.id.segment_length_value)

        segmentLengthSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress * 5 + 5 // Convertit la progression en une valeur de 5 à 60
                seekBarValue = progress
                segmentLengthValue.text =
                    value.toString() + "s" // Met à jour le TextView avec la valeur

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Non utilisé
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Non utilisé
            }
        })

        val numberPickerMinutesStart: NumberPicker = view.findViewById(R.id.numberPicker_minutes_start)
        val numberPickerSecondsStart: NumberPicker = view.findViewById(R.id.numberPicker_seconds_start)
        val numberPickerMinutesEnd: NumberPicker = view.findViewById(R.id.numberPicker_minutes_end)
        val numberPickerSecondsEnd: NumberPicker = view.findViewById(R.id.numberPicker_seconds_end)
        val maxTextSizeEditText: TextInputEditText = view.findViewById(R.id.edittext_max_text_size)
        numberPickerMinutesStart.minValue = 0
        numberPickerMinutesStart.maxValue = 59
        numberPickerSecondsStart.minValue = 0
        numberPickerSecondsStart.maxValue = 59
        numberPickerMinutesEnd.minValue = 0
        numberPickerMinutesEnd.maxValue = 59
        numberPickerSecondsEnd.minValue = 0
        numberPickerSecondsEnd.maxValue = 59

        numberPickerMinutesStart.setOnValueChangedListener { _, _, newVal ->
            Log.d("numberPickerMinutesStart", newVal.toString())
            val currentSeconds = startTimeInSeconds % 60
            startTimeInSeconds = newVal * 60 + currentSeconds

            // Assure que startTime ne soit pas supérieur à endTime
            if (startTimeInSeconds > endTimeInSeconds) {
                startTimeInSeconds = endTimeInSeconds
                numberPickerMinutesStart.value = startTimeInSeconds / 60
                Toast.makeText(requireContext(), "Le temps de début ne peut pas être supérieur au temps de fin", Toast.LENGTH_SHORT).show()
            }
        }

        numberPickerSecondsStart.setOnValueChangedListener { _, _, newVal ->
            Log.d("numberPickerSecondsStart", newVal.toString())
            val currentMinutes = startTimeInSeconds / 60
            startTimeInSeconds = currentMinutes * 60 + newVal

            // Assure que startTime ne soit pas supérieur à endTime
            if (startTimeInSeconds > endTimeInSeconds) {
                startTimeInSeconds = endTimeInSeconds
                numberPickerSecondsStart.value = startTimeInSeconds % 60
                Toast.makeText(requireContext(), "Le temps de début ne peut pas être supérieur au temps de fin", Toast.LENGTH_SHORT).show()
            }
        }

        numberPickerMinutesEnd.setOnValueChangedListener { _, _, newVal ->
            Log.d("numberPickerMinutesEnd", newVal.toString())
            val currentSeconds = endTimeInSeconds % 60
            endTimeInSeconds = newVal * 60 + currentSeconds

            // Assure que endTime ne soit pas inférieur à startTime
            if (endTimeInSeconds < startTimeInSeconds) {
                endTimeInSeconds = startTimeInSeconds
                numberPickerMinutesEnd.value = endTimeInSeconds / 60
                Toast.makeText(requireContext(), "Le temps de fin ne peut pas être inférieur au temps de début", Toast.LENGTH_SHORT).show()
            }
        }

        numberPickerSecondsEnd.setOnValueChangedListener { _, _, newVal ->
            Log.d("numberPickerSecondsEnd", newVal.toString())
            val currentMinutes = endTimeInSeconds / 60
            endTimeInSeconds = currentMinutes * 60 + newVal

            // Assure que endTime ne soit pas inférieur à startTime
            if (endTimeInSeconds < startTimeInSeconds) {
                endTimeInSeconds = startTimeInSeconds
                numberPickerSecondsEnd.value = endTimeInSeconds % 60
                Toast.makeText(requireContext(), "Le temps de fin ne peut pas être inférieur au temps de début", Toast.LENGTH_SHORT).show()
            }
        }

        buttonConfirm = view.findViewById(R.id.confirm_button)

        buttonConfirm.setOnClickListener {
            initialPrompt = initialPromptEditText.text.toString()
            maxTextSize = maxTextSizeEditText.text.toString().toInt()

            preferences.edit().apply {
                putString("languageCode", languageSelected)
                putString("languageIgnored", languageToIgnore)
                putBoolean("translate", translate)
                putBoolean("speed", speed)
                putInt("valueSeekbar", seekBarValue)
                putString("initialPrompt", initialPrompt)
                putInt("startTime", startTimeInSeconds)
                putInt("endTime", endTimeInSeconds)
                putInt("maxTextSize", maxTextSize)
                //putInt("LastPosition", -1)
            }.apply()

            val app = requireActivity().application as MyApplication
            if (languageSelected == "auto") {
                app.chosenLang = Locale.getDefault().language
            } else {
                app.chosenLang = languageSelected
            }

            val lastPosition = preferences.getInt("LastPosition", 0)

            if (lastPosition >= 0) {
                val intentStart = Intent(activity, MainActivity::class.java)
                startActivity(intentStart)
                preferences.edit().putInt("LastPosition", -1).apply()
                requireActivity().finish()
            } else {
                // Afficher un Toast pour indiquer que les paramètres ont été enregistrés
                Toast.makeText(activity, "Les paramètres ont bien été enregistrés !", Toast.LENGTH_SHORT).show()

                // Désactiver le bouton de confirmation
                buttonConfirm.isEnabled = false
            }
        }

        val lastPostion = preferences.getInt("LastPosition", 0)
        Log.d("MainActivity", "Position in Adapter: $lastPostion")
        if (lastPostion == -1) {
            val languagePrevious= preferences.getString("languageCode", null)
            val languageIgnoredPreviously = preferences.getString("languageIgnored", null)
            val translatePrevious = preferences.getBoolean("translate", false)
            val speedPrevious = preferences.getBoolean("speed", false)
            val positionToSelect = languages.indexOfFirst { it.second == languagePrevious }
            spinner.setSelection(positionToSelect)
            val positionToSelectForIgnore = languages.indexOfFirst { it.second == languageIgnoredPreviously }
            spinnerIgnore.setSelection(positionToSelectForIgnore)
            switchSpeed.isChecked = speedPrevious
            switchTranslate.isChecked = translatePrevious
            val previousInitialPrompt = preferences.getString("initialPrompt", "")
            val maxTextSize = preferences.getInt("maxTextSize", 1000)
            maxTextSizeEditText.setText(maxTextSize.toString())
            Log.d("Settings", "previousInitialPrompt: $previousInitialPrompt")
            initialPromptEditText.setText(previousInitialPrompt)

            spinnerIgnore.isEnabled = languagePrevious == "auto"
            val seekBarValuePrevious = preferences.getInt("valueSeekbar", 30)
            segmentLengthSeekBar.progress = seekBarValuePrevious
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val preferences = requireContext().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        val isRunning = preferences.getBoolean("isRunning", false)

        buttonConfirm.isEnabled = !isRunning
    }
}