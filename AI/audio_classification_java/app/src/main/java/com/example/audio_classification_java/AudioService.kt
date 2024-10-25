package com.example.audio_classification_java

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class AudioService : Service() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    override fun onCreate() {
        super.onCreate()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
              //입력되는 데시벨 크기를 상수로
//                 Log.d("sound","$rmsdB");
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // 오류 발생 시 다시 시작
                Toast.makeText(this@AudioService, "오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                startListening()
            }
            // 결과 처리
            override fun onResults(results: Bundle?) {
                // 결과 인식을 matches로 저장
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                // SPEECH_RECOGNITION_RESULT라는 이름의 intent 생성
                val intent = Intent("SPEECH_RECOGNITION_RESULT")
                // 해당 intent에 matches라는 이름의 matches 결과 배열 리스트를 넣어둠
                intent.putStringArrayListExtra("matches", matches)
                // matches의 값 확인하는 로그
                Log.e("11111111111111111", "$matches")
                // LocalBroadcastManager는 안드로이드에서 앱 내부의 컴포넌트 간 통신을 위해 사용되는 클래스
                // 일반 BroadcastManager와 달리, LocalBroadcastManager는 앱 내부에서만 작동하므로 보안성이 높고 효율적입니다
                // AudioService의 context에서 instance를 가져와서 intent에 넣고 다른 곳에서 사용할 수 있게 띄운다.
                LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(intent)
                startListening() // 결과 처리 후 다시 시작
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        // 음성 인식을 위한 Intent를 설정하는 '준비' 과정
        // apply를 통해 추가 정보를 설정하는 것
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Free_FORM : 언어 모델이 자유 형식의 음성을 인식하도록 지정-> 일반적인 대화나 다양한 주제의 음성을 인식하는 데 적합
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // 서비스를 호출하는 앱 패키지 이름을 지정 -> 인식 결과를 올바른 앱으로 반환
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        startListening()
    }
    // 인식 시작하기
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