package com.example.audio2text

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionaries")
    fun getAllDictionaries(): LiveData<List<Dictionary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(dictionary: Dictionary)

    @Transaction
    fun insertOrUpdate(dictionary: Dictionary) {
        val id: String = dictionary.id
        val existingItem = getDictionaryById(id)
        if (existingItem != null) {
            Log.d("DAO", "Mise à jour du dictionnaire avec l'ID: $id")
            update(dictionary)
        } else {
            Log.d("DAO", "Insertion d'un nouveau dictionnaire avec l'ID: $id")
            insert(dictionary)
        }
    }

    @Update
    fun update(dictionary: Dictionary)

    @Query("SELECT * FROM dictionaries WHERE id = :id")
    fun getDictionaryById(id: String): Dictionary?

    @Query("DELETE FROM dictionaries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Update
    fun updateAll(entities: List<Dictionary>)
}