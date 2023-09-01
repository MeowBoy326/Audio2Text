package com.example.audio2text

object DictionaryManager {
    val listDictionaries = mutableListOf<DictionaryItem>()

    init {
        for ((language, code) in SettingsFragment.languages.drop(1)) {
            val name = language
            val filePath = "dic_${code}.txt"
            listDictionaries.add(DictionaryItem(code, name, filePath, false))
        }
    }

    fun getDictionaryItemById(id: String): DictionaryItem? {
        return listDictionaries.find { it.id == id }
    }
}