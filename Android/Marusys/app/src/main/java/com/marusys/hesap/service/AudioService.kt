package com.marusys.hesap.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.marusys.hesap.AudioClassifier
import com.marusys.hesap.feature.VoiceRecognitionEngine
import com.marusys.hesap.feature.VoiceRecognitionState
import com.marusys.hesap.feature.VoiceStateManager
import com.marusys.hesap.presentation.components.Notification
import com.marusys.hesap.presentation.components.OverlayContent

private val TAG = "AudioService"

class AudioService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

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
    // 오베리이 관련
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG,"오디오 서비스 시작 11111111111111111111111111")
        VoiceStateManager.updateState(VoiceRecognitionState.HotwordDetecting)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        notificationManager = Notification(this)
        classifier = AudioClassifier(this)  // AudioClassifier 초기화
        // 윈도우 매니져 서비스 시작
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // 카메라 초기화- 손전등 관련 코드
        initializeCamera()
        // SpeechRecognizer 시작
        initializeSpeechRecognizer()
        // 포 그라운드 시작
//        startForeground(NOTIFICATION_ID, createNotification())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening() // 명령 인식 시작
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
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
            matches?.firstOrNull()?.let { command ->
                if (executeCommand(command)) { stopListening()}
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
        // 오버레이
        if (overlayView == null) {
            overlayView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@AudioService)
                setViewTreeSavedStateRegistryOwner(this@AudioService)

                setContent {
                    OverlayContent { stopListening() }
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL  // 하단 중앙에 위치
            }

            windowManager.addView(overlayView, params)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
    }

    private fun executeCommand(command: String) : Boolean {
        var executeCommant = true
        when {
            // 명령어 하드 코딩
            command.contains("손전등 켜", ignoreCase = true) -> toggleFlashlight(true)
            command.contains("손전등 꺼", ignoreCase = true) -> toggleFlashlight(false)
            else -> executeCommant = false
        }
        return executeCommant
    }
    private fun stopListening() {
        val intent = Intent(this, AudioService::class.java )
//        speechRecognizer.stopListening()
//        speechRecognizer.destroy()
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        VoiceStateManager.updateState(VoiceRecognitionState.WaitingForHotword) // 키워드 대기상태
        stopService(intent)
    }
    // 손전등 on off
    private fun toggleFlashlight(on: Boolean) {
        cameraId?.let { id ->
            cameraManager.setTorchMode(id, on)
        }
    }
}