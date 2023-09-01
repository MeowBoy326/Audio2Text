package com.example.audio2text

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MyAdapter(private var dictionaries: List<DictionaryItem>, lifecycleOwner: LifecycleOwner, private val onItemClicked: (Int) -> Unit) :
    RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

    private var selectedPosition = -1

    inner class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textView)
        val buttonDownload: ImageButton = view.findViewById(R.id.download_button)
        val buttonDelete: ImageButton = view.findViewById(R.id.delete_button)
        val progress: ProgressBar = view.findViewById(R.id.progressBarDictionary)
    }

    init {
        DictionaryViewModelHolder.viewModel.dictionaryItems.observe(lifecycleOwner) { updatedList ->
            dictionaries = updatedList
            val positionToUpdate = updatedList.indexOfFirst { it.id == DictionaryViewModelHolder.viewModel.lastUpdatedId }
            Log.d("TAG", "going to update: ${positionToUpdate}")
            if (positionToUpdate != -1) {
                Log.d("TAG", "updated: ${dictionaries[positionToUpdate].name} at position: ${positionToUpdate}")
                notifyItemChanged(positionToUpdate)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MyAdapter.MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyAdapter.MyViewHolder, position: Int) {
        val item = dictionaries[position]
        holder.textView.text = item.name
        holder.buttonDownload.setOnClickListener {
            // Votre logique pour le téléchargement
            val context = holder.itemView.context // Obtenir le contexte
            holder.buttonDownload.isEnabled = false
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra("dictionaryId", item.id)
            }
            context.startService(intent)
        }
        holder.buttonDelete.setOnClickListener {
            // Votre logique pour la suppression
            holder.textView.setTypeface(null, Typeface.NORMAL)
            item.isDownloadComplete = false
            holder.buttonDownload.visibility = View.VISIBLE
            holder.progress.progress = 0
            holder.buttonDelete.visibility = View.GONE
            CoroutineScope(Dispatchers.IO).launch {
                DictionaryViewModelHolder.viewModel.deleteDictionary(item.id)
            }
        }

        holder.itemView.setOnClickListener {
            item.isSelected = !item.isSelected
            onItemClicked(holder.layoutPosition)
        }

        if (item.isDownloadComplete) {
            Log.d("MyAdapter", "dictionaryItem.name: ${item.name} at position: ${position}")
            holder.progress.progress = 100
            holder.buttonDownload.visibility = View.GONE
            holder.buttonDelete.visibility = View.VISIBLE
            holder.textView.setTypeface(null, Typeface.BOLD)
        } else if (item.isDownloading) {
            holder.buttonDownload.isEnabled = false
            holder.buttonDelete.visibility = View.GONE
            holder.progress.progress = item.progress
        } else if (item.isFailed) {
            holder.buttonDownload.isEnabled = true
            holder.buttonDelete.visibility = View.GONE
            // montrer une icône ou un message d'échec si nécessaire
        } else {
            // État par défaut
            holder.buttonDownload.isEnabled = true
            holder.buttonDelete.visibility = View.GONE
            holder.progress.progress = 0
            holder.textView.setTypeface(null, Typeface.NORMAL)
        }

        if (item.isSelected) {
            holder.itemView.setBackgroundColor(Color.WHITE)  // Mettez la couleur que vous voulez
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorBackgroundDico)) // Mettez la couleur par défaut
        }
    }

    override fun getItemCount() = dictionaries.size
}