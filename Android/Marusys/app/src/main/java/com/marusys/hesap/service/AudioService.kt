package com.marusys.hesap.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.AndroidSpeechRecognizer
import com.marusys.hesap.AudioClassifier
import com.marusys.hesap.feature.VoiceRecognitionEngine
import com.marusys.hesap.feature.VoiceRecognitionState
import com.marusys.hesap.feature.VoiceStateManager
import com.marusys.hesap.presentation.components.Notification
import com.marusys.hesap.presentation.components.Notification.Companion.NOTIFICATION_ID

private val TAG = "AudioService"

class AudioService : Service() {
//    private var islistening  = false  // 실시간 감지 상태를 추적하는 변수
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var classifier: AudioClassifier  // AudioClassifier 인스턴스
    private lateinit var voiceEngine: VoiceRecognitionEngine
    // 손전등 관련
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    // 알림창
    private lateinit var notificationManager: Notification


    override fun onCreate() {
        super.onCreate()
        Log.e(TAG,"오디오 서비스 시작 11111111111111111111111111")
        VoiceStateManager.updateState(VoiceRecognitionState.HotwordDetecting)
        notificationManager = Notification(this)
        classifier = AudioClassifier(this)  // AudioClassifier 초기화
        // 카메라 초기화
        initializeCamera()
        initializeSpeechRecognizer()
        // SpeechRecognizer 시작
//        voiceEngine = AndroidVoiceEngine(this, engineCallback)
        // 포 그라운드 시작
//        startForeground(NOTIFICATION_ID, createNotification())
//        voiceEngine.startHotwordDetection()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        VoiceStateManager.updateState(VoiceRecognitionState.WaitingForHotword) // 키워드 대기상태

    }
    // 음성녹음 초기화
    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(recognitionListener)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }
    // 카메라 초기화
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
            Log.e(TAG,"results $matches")
            val intent = Intent("SPEECH_RECOGNITION_RESULT")
            intent.putStringArrayListExtra("matches", matches)
            LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(intent)
            startListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.e(TAG,"partialResults = $matches")
        }

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
        override fun onError(error: Int) {
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
            Log.e("AudioService", "Speech recognition error: $errorMessage")
            Handler(Looper.getMainLooper()).postDelayed({startListening()},1000)
        }
    }
    private fun startListening() {
        speechRecognizer.startListening(recognizerIntent)
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        return START_STICKY
    }

    private fun executeCommand(command: String) {
        when {
            command.contains("손전등 켜", ignoreCase = true) -> toggleFlashlight(true)
            command.contains("손전등 꺼", ignoreCase = true) -> toggleFlashlight(false)
            // 다른 명령어 처리...
        }
    }
    // 손전등 on off
    private fun toggleFlashlight(on: Boolean) {
        cameraId?.let { id ->
            cameraManager.setTorchMode(id, on)
        }
    }

}