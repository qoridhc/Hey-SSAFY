package com.marusys.hesap.presentation.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel() : ViewModel() {
    private val _resultText = MutableStateFlow("") // viewModel 내에서 함수를 통해서만 수정하기 위해
    val resultText = _resultText.asStateFlow() // 외부에서 바꿔 쓰지 않지만, 읽기 위해 정의

    private val _memoryText = MutableStateFlow("")
    val memoryText = _memoryText.asStateFlow()

    fun setResultText(text : String){
        _resultText.value = text
    }
    fun setMemoryText(text : String){
        _memoryText.value = text
    }
}