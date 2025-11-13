package com.example.testmessagesimple

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MessagingViewModelFactory(
    private val application: Application,
    private val token: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessagingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MessagingViewModel(application, token) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

