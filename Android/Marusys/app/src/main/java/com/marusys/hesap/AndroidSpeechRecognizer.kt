package com.marusys.hesap

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract.Instances
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.feature.VoiceRecognitionEngine
import com.marusys.hesap.feature.VoiceRecognitionState
import com.marusys.hesap.feature.VoiceStateManager
import com.marusys.hesap.service.AudioService

class AndroidSpeechRecognizer(
    private val context: Context,
    private val callback: VoiceRecognitionEngine.Callback
) : VoiceRecognitionEngine {
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    // 음성 인식을 위한 Intent를 설정하는 '준비' 과정 (최적화)
        // apply를 통해 추가 정보를 설정하는 것
    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                // FREE_FORM : 언어 모델이 자유 형식의 음성을 인식하도록 지정-> 일반적인 대화나 다양한 주제의 음성을 인식하는 데 적합
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // 서비스를 호출하는 앱 패키지 이름을 지정 -> 인식 결과를 올바른 앱으로 반환
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // 반환할 최대 인식 결과 수, 가장 가능성 높은 거만
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // 최소 밀리 세컨드 이상
//            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            // 1초 정도 정적이 있으면 음성 인식을 완료됐을 가능성 있다고 판단
//            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
            // 일리 세컨드 정도 완전한 침묵 = 입력 완
//            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
        }
    init {
        setupRecognitionListener()
    }

    private fun setupRecognitionListener() {
        Log.d("setupRecognitionListener","setupRecognitionListener start")
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { result -> callback.onCommandRecognized(result) }
                Log.d("setupRecognitionListener onResult",matches.toString())
            }

            override fun onPartialResults(partialResults: Bundle?) {
                TODO("Not yet implemented")
            }

            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                Log.e("SpeechRecognizer", "Error occurred: $errorMessage")
                callback.onError(errorMessage)
                VoiceStateManager.updateState(VoiceRecognitionState.WaitingForHotword)
//                callback.onError(getErrorMessage(error))
            }

            // 다른 메서드들 구현

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizer", "onReadyForSpeech to listen...")
            }
            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "onBeginningOfSpeech to listen...")
            }
            override fun onRmsChanged(rmsdB: Float) {
                //입력되는 데시벨 크기를 상수로
                // Log.d("sound","$rmsdB");
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
        })
    }


    override fun startCommandRecognition() {
        Log.d("SpeechRecognizer", "Starting to listen...")
        speechRecognizer.startListening(recognizerIntent)
    }

    override fun stopRecognition() {
        speechRecognizer.stopListening()
    }

    override fun destroy() {
        speechRecognizer.destroy()
    }
    fun getErrorMessage(error : Int) : String{
    val errorMessage = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "오디오 녹음 오류"
        SpeechRecognizer.ERROR_CLIENT -> "클라이언트 측 오류"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "권한 부족"
        SpeechRecognizer.ERROR_NETWORK -> "네트워크 오류"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 시간 초과"
        SpeechRecognizer.ERROR_NO_MATCH -> "일치하는 음성 없음"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기 사용 중"
        SpeechRecognizer.ERROR_SERVER -> "서버 오류"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성 입력 시간 초과"
        else -> "알 수 없는 오류"
    }
        return errorMessage
    }
}