//package com.marusys.hesap
//
//import android.content.Context
//import android.speech.SpeechRecognizer
//import com.marusys.hesap.feature.VoiceRecognitionEngine
//import com.marusys.hesap.feature.VoiceRecognitionState
//
//// AndroidVoiceEngine.kt
//class AndroidVoiceEngine(
//    private val context: Context,
//    private val callback: VoiceRecognitionEngine.Callback
//) : VoiceRecognitionEngine {
//    private val audioClassifier = AudioClassifier(context)
//    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
//    private var currentState: VoiceRecognitionState = VoiceRecognitionState.WaitingForHotword
//
//    override fun startHotwordDetection() {
//        currentState = VoiceRecognitionState.HotwordDetecting
//        callback.onStateChanged(currentState)
//
//        // 호출어 감지 로직
//        startAudioRecording { audioData ->
//            val results = audioClassifier.classify(audioData)
//            if (results[0] >= 0.9f) {
//                callback.onHotwordDetected()
//            }
//        }
//    }
//
//    override fun startCommandRecognition() {
//        currentState = VoiceRecognitionState.CommandListening
//        callback.onStateChanged(currentState)
//
//        // 명령어 인식 로직
//        speechRecognizer.startListening(createRecognizerIntent())
//    }
//
//    override fun stopRecognition() {
//        TODO("Not yet implemented")
//    }
//
//    override fun destroy() {
//        TODO("Not yet implemented")
//    }
//
//    // 기타 구현...
//}