package com.marusys.hesap

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class AudioService : Service() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    override fun onCreate() {
        super.onCreate()
        initializeSpeechRecognizer()
        initializeCamera()
//        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(recognitionListener)
        // apply를 통해 추가 정보를 설정하는 것
        // 음성 인식을 위한 Intent를 설정하는 '준비' 과정 (최적화)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Free_FORM : 언어 모델이 자유 형식의 음성을 인식하도록 지정-> 일반적인 대화나 다양한 주제의 음성을 인식하는 데 적합
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // 서비스를 호출하는 앱 패키지 이름을 지정 -> 인식 결과를 올바른 앱으로 반환
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            // 반환할 최대 인식 결과 수, 가장 가능성 높은 거만
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // 최소 0.5초 이상
//            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
            // 1초 정도 정적이 있으면 음성 인식을 완료됐을 가능성 있다고 판단
//            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
            // 0.5초 정도 완전한 침묵 = 입력 완
//            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 500)
        }
    }

    private fun initializeCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { result ->
                if (result.contains("손전등 켜", ignoreCase = true)) {
                    toggleFlashlight(true)
                } else if (result.contains("손전등 꺼", ignoreCase = true)) {
                    toggleFlashlight(false)
                }
            }
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
            // 계속해서 음성 인식 수행
            startListening()
        }
                    override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                //입력되는 데시벨 크기를 상수로
//                 Log.d("sound","$rmsdB");
            }
                    override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                  // 오류 발생 시 다시 시작
                // error 코드 7 : 주위가 시끄러움
//                if (error == 7){
//                    Toast.makeText(this@AudioService, "죄송해요 소음때문에 못들었어요", Toast.LENGTH_SHORT).show()
//                }
                startListening()
            }
    }

    // 인식 시작하기
    private fun startListening() {
        speechRecognizer.startListening(recognizerIntent)
    }

    private fun toggleFlashlight(on: Boolean) {
        cameraId?.let { id ->
            cameraManager.setTorchMode(id, on)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }

    // 포그라운드 서비스를 위한 알림 생성
//    private fun createNotification(): Notification {}
    companion object {
        private const val NOTIFICATION_ID = 1
    }
}