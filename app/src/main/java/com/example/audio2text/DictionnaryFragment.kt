package com.example.audio2text

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton


class DictionaryFragment : Fragment() {
    private var sharedPreferences: SharedPreferences? = null
    private var languageCode: String? = null
    private var listDictionaries = mutableListOf<DictionaryItem>()
    var selectedPosition = -1
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_dictionary, container, false)

        listDictionaries = DictionaryManager.listDictionaries
        sharedPreferences =
            requireActivity().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        languageCode = sharedPreferences!!.getString("languageCode", "auto")
        val btnConfirm = view.findViewById<Button>(R.id.btn_save)

        val recyclerView: RecyclerView = view.findViewById(R.id.list_downloaded_dictionaries)
        var selectedDictionaryId: String? = null
        var myAdapter: MyAdapter? = null

        myAdapter = MyAdapter(listDictionaries, viewLifecycleOwner) { position ->
            // Désélectionnez l'élément précédemment sélectionné
            if (selectedPosition != -1 && position == selectedPosition) {
                listDictionaries[selectedPosition].isSelected = false
                DictionaryViewModelHolder.viewModel.selectDictionary(selectedDictionaryId!!, false)
                selectedDictionaryId = null
                selectedPosition = -1
            } else if (selectedPosition == -1) {
                // Sélectionnez le nouvel élément
                selectedPosition = position
                // Mettez à jour l'ID du dictionnaire sélectionné et activez le bouton "Confirmer"
                selectedDictionaryId = listDictionaries[position].id
                DictionaryViewModelHolder.viewModel.selectDictionary(selectedDictionaryId!!, true)
            } else {
                Toast.makeText(requireContext().applicationContext, "Vous ne pouvez sélectionner qu'un dictionnaire à la fois !", Toast.LENGTH_SHORT).show()
            }
        }

        val isRunning = sharedPreferences?.getBoolean("isRunning", false)

        btnConfirm.isEnabled = !isRunning!!

        val dictionaryDao =
            AppDatabase.getDatabase(requireContext().applicationContext).dictionaryDao()
        dictionaryDao.getAllDictionaries()
            .observe(viewLifecycleOwner, object : Observer<List<Dictionary>> {
                override fun onChanged(value: List<Dictionary>) {
                    listDictionaries.forEach { item ->
                        val dbItem = value.firstOrNull { it.id == item.id }

                        item.isDownloadComplete = dbItem?.isDownloaded ?: false
                        myAdapter.notifyItemChanged(listDictionaries.indexOf(item))
                    }
                }
            })

        val selectedItem = sharedPreferences!!.getString("selectedDictionaryId", null)
        if (selectedItem != null) {
            val targetItem = listDictionaries.firstOrNull { it.id == selectedItem }
            if (targetItem != null) {
                targetItem.isSelected = true
                myAdapter.notifyItemChanged(listDictionaries.indexOf(targetItem))
            }
        }

        recyclerView.adapter = myAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        btnConfirm.setOnClickListener {
            val preferences =
                requireContext().applicationContext.getSharedPreferences(
                    "MyPreferences",
                    Context.MODE_PRIVATE
                )
            val lastPosition = preferences.getInt("LastPosition", 0)
            Log.d("DictionaryFragment", "selectedDictionaryId: $selectedDictionaryId")
            if (selectedDictionaryId != null) {
                val sharedPreferences: SharedPreferences =
                    requireContext().applicationContext.getSharedPreferences(
                        "MyPreferences",
                        Context.MODE_PRIVATE
                    )
                val editor: SharedPreferences.Editor = sharedPreferences.edit()
                editor.putString("selectedDictionaryId", selectedDictionaryId)
                editor.apply()

                // Afficher un Toast pour indiquer que les paramètres ont été enregistrés
                Toast.makeText(
                    activity,
                    "Le dictionnaire a bien été configuré !",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // Montrer un message indiquant qu'aucun dictionnaire n'a été sélectionné
                Toast.makeText(
                    activity,
                    "Aucun dictionnaire n'a été sélectionné !",
                    Toast.LENGTH_SHORT
                ).show()
            }

            if (lastPosition >= 0) {
                // Charger l'animation de translation
                // Appellez moveToPage() sur l'activité pour changer de page
                (activity as? OnboardingPageChangeListener)?.moveToPage(5)
            }
            requireContext().applicationContext.stopService(Intent(context, DownloadService::class.java))
        }

        return view
    }
}