package com.example.testmessagesimple

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.testmessagesimple.utils.TokenManager

class AuthViewModelFactory(
    private val tokenManager: TokenManager,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(tokenManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}