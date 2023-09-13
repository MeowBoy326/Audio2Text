package com.example.audio2text

import android.content.Context
import android.util.Log
import io.gitlab.rxp90.jsymspell.SymSpell
import io.gitlab.rxp90.jsymspell.SymSpellBuilder
import io.gitlab.rxp90.jsymspell.SymSpellImpl
import io.gitlab.rxp90.jsymspell.TokenWithContext
import io.gitlab.rxp90.jsymspell.Verbosity
import io.gitlab.rxp90.jsymspell.api.Bigram
import io.gitlab.rxp90.jsymspell.api.SuggestItem
import io.gitlab.rxp90.jsymspell.exceptions.NotInitializedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class SpellChecker private constructor() {

    companion object Builder {
        private var maxEditDistance: Int = 2  // valeur par défaut
        val frequencyMap: HashMap<String, Long> = HashMap()
        val bigramMap: HashMap<Bigram, Long> = HashMap()
        private lateinit var symSpell: CompletableFuture<SymSpellImpl>

        suspend fun loadBigramDictionary(context: Context, filename: String): Builder {
            withContext(Dispatchers.IO) {
                val absoluteFilePath = File(context.filesDir, filename).absolutePath
                val inputStream = FileInputStream(absoluteFilePath)
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))

                bufferedReader.forEachLine { line ->
                    val parts = line.split(" ")
                    if (parts.size >= 3) {
                        val word1 = parts[0]
                        val word2 = parts[1]
                        val frequency = parts[2].toLongOrNull() ?: 0L
                        val bigram = Bigram(word1, word2)
                        bigramMap[bigram] = frequency
                    }
                }

                bufferedReader.close()
            }

            return this
        }

        suspend fun loadFrequencyDictionary(context: Context, filename: String): Builder {
            withContext(Dispatchers.IO) {
                val absoluteFilePath = File(context.filesDir, filename).absolutePath
                val inputStream = FileInputStream(absoluteFilePath)
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                bufferedReader.forEachLine { line ->
                    //Log.d("loadFrequencyDictionary", "Ligne : $line")
                    val parts = line.split("\t")
                    if (parts.size >= 3) { // Changé à 3 au lieu de 2
                        val word = parts[1]  // Changé à 1 au lieu de 0
                        val frequency = parts[2].toLongOrNull() ?: 0L  // Changé à 2 au lieu de 1
                        frequencyMap.put(word, frequency)
                        //Log.d("loadFrequencyDictionary", "Mot : $word, Fréquence : $frequency")
                    } else {
                        Log.e("loadFrequencyDictionary", "Ligne mal formée : $line")
                    }
                }

                bufferedReader.close()
            }

            return this
        }

        fun withMaxEditDistance(maxEditDistance: Int): Builder {
            this.maxEditDistance = maxEditDistance
            return this
        }

        fun buildAsync(): CompletableFuture<SpellChecker> {
            return CompletableFuture.supplyAsync {
                if (frequencyMap.isEmpty()) {
                    throw Exception("La carte de fréquence est vide")
                }

                if (bigramMap.isEmpty()) {
                    throw Exception("La carte des bigrammes est vide")
                }

                Log.d("SpellChecker", "frequencyMap: ${frequencyMap.size}")
                Log.d("SpellChecker", "bigramMap: ${bigramMap.size}")

                // Mettre à jour SymSpell ici
                symSpell = SymSpellBuilder()
                    .setUnigramLexicon(frequencyMap)
                    .setBigramLexicon(bigramMap)
                    .setMaxDictionaryEditDistance(maxEditDistance)
                    .createSymSpell()

                SpellChecker()
            }
        }
    }

    fun suggest(
        misspelledPhrase: String,
        maxEditDistance: Int = SpellChecker.maxEditDistance
    ): CompletableFuture<List<TokenWithContext>> {
        return symSpell.thenApplyAsync { completedSymSpell ->
            val suggestions = completedSymSpell.lookupCompound(misspelledPhrase, maxEditDistance, false, Verbosity.BEST_WITHIN_MAX_EDIT_DISTANCE, 3)
            Log.d("SpellChecker", "suggestions size: ${suggestions.size}")
            Log.d("SpellChecker", "misspelledPhrase: $misspelledPhrase")
            val allSuggestions = suggestions.flatMap { tokenWithContext ->
                tokenWithContext.suggestions.map { suggestItem ->
                    suggestItem.suggestion // ou suggestItem.toString() si vous voulez toute l'information
                }
            }
            Log.d("SpellChecker", "longest suggestion: ${allSuggestions.maxByOrNull { it.length }}")
            suggestions
        }
    }

        fun suggestWord(
            misspelledWord: String,
            verbosity: Verbosity = Verbosity.CLOSEST,
            includeUnknown: Boolean = false
        ): CompletableFuture<List<String>> {
            return symSpell.thenApplyAsync { completedSymSpell ->
                try {
                    val suggestions =
                        completedSymSpell.lookup(misspelledWord, verbosity, includeUnknown)
                    Log.d("SpellChecker", "suggestions: ${suggestions.size}")
                    Log.d("SpellChecker", "misspelledWord: $misspelledWord")
                    suggestions.map { it.suggestion }
                } catch (e: NotInitializedException) {
                    Log.e("SpellChecker", "SymSpell not initialized")
                    emptyList<String>()
                }
            }
        }
}