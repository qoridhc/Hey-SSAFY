package com.example.audio_classification_java

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.audio_classification_java.presentation.viewmodel.MainViewModel

class AudioService : Service() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    val viewModel = MainViewModel()
    override fun onCreate() {
        super.onCreate()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                 Log.d("sound",""+rmsdB);
              //입력되는 데시벨 크기를 상수로
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // 오류 발생 시 다시 시작
                startListening()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                // 결과 처리
                val rs: Array<String> = matches?.let { Array(it.size) { "" } } ?: emptyArray()


                matches?.toArray(rs);
              //rs에 인식된 결과 값이 들어가 있다.
                viewModel.setResultText("1" + rs)
                startListening() // 결과 처리 후 다시 시작
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        startListening()
    }

    private fun startListening() {
        speechRecognizer.startListening(recognizerIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}