package com.example.audio2text

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.InputStreamReader

class SpellChecker private constructor(distanceFunc: (String, String) -> Int) {

    companion object Builder {
        private var distanceFunc: (String, String) -> Int = ::LevenshteinDistance
        private val frequencyMap: HashMap<String, Int> = HashMap()

        fun loadFrequencyDictionary(context: Context, filename: String): Builder {
            val absoluteFilePath = File(context.filesDir, filename).absolutePath
            val inputStream = FileInputStream(absoluteFilePath)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            Log.d("TAG", "loadFrequencyDictionary: $absoluteFilePath")

            bufferedReader.forEachLine { line ->
                val parts = line.split("\t")
                if (parts.size >= 2) {
                    val word = parts[1]
                    val frequency = parts[2].toIntOrNull() ?: 0
                    frequencyMap[word] = frequency
                }
            }
            return this
        }

        fun withEditDistanceFunction(func: (str1: String, str2: String) -> Int): Builder {
            distanceFunc = func
            return this
        }

        fun build(): SpellChecker {
            if (frequencyMap.isEmpty()) {
                throw Exception("please specify frequency map")
            }
            return SpellChecker(distanceFunc)
        }

    }

    private val bkTree: BKTree

    init {
        bkTree = BKTree(distanceFunc)
        for (word in frequencyMap.keys) {
            bkTree.add(word)
        }
    }

    val totalWords: Int get() = frequencyMap.size

    fun suggest(misspellWord: String, tolerance: Int = 1): List<String> {
        val suggestions = bkTree.getSpellSuggestion(misspellWord, tolerance)
        return suggestions.sortedBy { word ->
            -frequencyMap.getOrDefault(word, 0)
        }
    }

}