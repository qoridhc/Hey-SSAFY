package com.example.audio_classification_java.presentation.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel() : ViewModel() {
    // viewModel로 페이지간 공통해서 쓰는 변수를 공유함
    // 앱의 생명주기와 같이 하기 때문에,
    // 다양한 방법이 있는데, 그 중 하나 로서 화면이 샐행될 때 값이 초기화 되지 않게 하기 위함
    private val _resultText = MutableStateFlow("") // viewModel 내에서 함수를 통해서만 수정하기 위해
    val resultText = _resultText.asStateFlow() // 외부에서 바꿔 쓰지 않지만, 읽기 위해 정의

    // viewModel 내 변수 수정을 위한 함수
    fun setResultText(text : String){
        _resultText.value = text
    }

//    private val _errorText = MutableStateFlow("")
//    val errorText = _errorText.asStateFlow()
//
//    fun setErrorText(text : String){
//        _errorText.value = text
//    }
}