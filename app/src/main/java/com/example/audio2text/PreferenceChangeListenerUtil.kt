package com.example.audio2text

import android.content.SharedPreferences
import android.util.Log

object PreferenceChangeListenerUtil {
    val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        when (key) {
            "isRunning" -> {
                val value = preferences?.getBoolean(key, false)!!
                HomeViewModelHolder.viewModel.updateLiveData(key, value)
            }

            "isFullyStopped" -> {
                val value = preferences?.getBoolean(key, false)!!
                HomeViewModelHolder.viewModel.updateLiveData(key, value)
            }

            "isFullySuccessful" -> {
                val value = preferences?.getBoolean(key, false)!!
                HomeViewModelHolder.viewModel.updateLiveData(key, value)
            }

            "isGoingToStop" -> {
                val value = preferences?.getBoolean(key, false)!!
                HomeViewModelHolder.viewModel.updateLiveData(key, value)
            }
            "isInitialized" -> {
                val value = preferences?.getBoolean(key, false)!!
                HomeViewModelHolder.viewModel.updateLiveData(key, value)
            }
        }
    }
}