package com.marusys.hesap.feature

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// RecordRecognitionState.kt
sealed class RecordRecognitionState {
    object WaitingForKeyword : RecordRecognitionState()
    object KeywordDetected : RecordRecognitionState()
    object ListeningCommand : RecordRecognitionState()
    object ExecutingCommand : RecordRecognitionState()
}
// RecordStateManager.kt
object RecordStateManager {
    private val _recordState = MutableStateFlow<RecordRecognitionState>(RecordRecognitionState.WaitingForKeyword)
    val recordState = _recordState.asStateFlow()

    fun updateState(newState: RecordRecognitionState) {
        _recordState.value = newState
    }
}

interface VoiceRecognizer {
    fun startListening()
    fun stopListening()
    fun destroy()

    interface Callback {
//        fun onResults(results:  ArrayList<String>?)
        fun onResult(result : String) // 인식한 값
        fun onError(error: String)
        fun onPartialResult(text: String)
    }
}