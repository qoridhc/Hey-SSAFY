package com.marusys.hesap.feature

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// VoiceRecognitionState.kt
sealed class VoiceRecognitionState {
    object WaitingForHotword : VoiceRecognitionState()
    object HotwordDetecting : VoiceRecognitionState()
    object CommandListening : VoiceRecognitionState()
    data class Error(val message: String) : VoiceRecognitionState()
}

// RecordStateManager.kt
object VoiceStateManager {
    private val _voiceState = MutableStateFlow<VoiceRecognitionState>(VoiceRecognitionState.WaitingForHotword)
    val voiceState = _voiceState.asStateFlow()

    fun updateState(newState: VoiceRecognitionState) {
        _voiceState.value = newState
    }
}

// VoiceRecognitionEngine.kt
interface VoiceRecognitionEngine {
    fun startCommandRecognition()
    fun stopRecognition()
    fun destroy()

    interface Callback {
        fun onHotwordDetected()
        fun onCommandRecognized(command: String)
        fun onError(error: String)
        fun onStateChanged(state: VoiceRecognitionState)
    }
}