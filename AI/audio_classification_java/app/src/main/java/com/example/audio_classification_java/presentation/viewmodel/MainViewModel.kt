package com.example.audio_classification_java.presentation.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel() : ViewModel() {
    private val _resultText = MutableStateFlow("")
    val resultText = _resultText.asStateFlow()

    fun setResultText(text : String){
        _resultText.value = text
    }

    private val _errorText = MutableStateFlow("")
    val errorText = _errorText.asStateFlow()

    fun setErrorText(text : String){
        _errorText.value = text
    }
}