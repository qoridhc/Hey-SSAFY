package com.marusys.hesap.service

import android.R as AndroidR
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.MainActivity

private val TAG = "AudioService"

class AudioService : Service() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var cameraManager: CameraManager
    private val handler = Handler(Looper.getMainLooper())
    private var cameraId: String? = null
    private var isListening = false // 음성 인식 상태를 추적하는 변수 추가

    override fun onCreate() {
        super.onCreate()
        Log.d("AudioService", "Starting foreground service")
        startForeground(NOTIFICATION_ID, createNotification())
        initializeSpeechRecognizer()
        initializeCamera()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            // 음성 인식 완료 후 상태 업데이트
            isListening = false
            // 음성 인식된 단어가 리스트형태로
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { result ->
                if (result.contains("손전등 켜", ignoreCase = true)) {
                    toggleFlashlight(true)
                } else if (result.contains("손전등 꺼", ignoreCase = true)) {
                    toggleFlashlight(false)
                } else if (result.contains("헤이 사피", ignoreCase = true)) {
                    val intent = Intent(this@AudioService, OverlayService::class.java)
                    intent.action = "SHOW_OVERLAY"
                    startService(intent)
                }
            }
            // SPEECH_RECOGNITION_RESULT라는 이름의 intent 생성
            val intent = Intent("SPEECH_RECOGNITION_RESULT")
            // 해당 intent에 matches라는 이름의 matches 결과 배열 리스트를 넣어둠
            intent.putStringArrayListExtra("matches", matches)
            // matches의 값 확인하는 로그
            Log.e("들어가 있는 텍스트", "$matches")
            // LocalBroadcastManager는 안드로이드에서 앱 내부의 컴포넌트 간 통신을 위해 사용되는 클래스
            // 일반 BroadcastManager와 달리, LocalBroadcastManager는 앱 내부에서만 작동하므로 보안성이 높고 효율적입니다
            // AudioService의 context에서 instance를 가져와서 intent에 넣고 다른 곳에서 사용할 수 있게 띄운다.
            LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(intent)
            // 계속해서 음성 인식 수행
            handler.postDelayed({ startListening() }, 300)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            //입력되는 데시벨 크기를 상수로
            // Log.d("sound","$rmsdB");
        }

        override fun onPartialResults(partialResults: Bundle?) {}
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
            // 에러 발생 시 상태 업데이트
            isListening = false
            Log.e("AudioService", "Speech recognition error: $errorMessage")
            // 일정 시간 후에 다시 시작
            handler.postDelayed({
                startListening()
            }, 2000)
        }
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(recognitionListener)
        // 음성 인식을 위한 Intent를 설정하는 '준비' 과정 (최적화)
        // apply를 통해 추가 정보를 설정하는 것
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                // FREE_FORM : 언어 모델이 자유 형식의 음성을 인식하도록 지정-> 일반적인 대화나 다양한 주제의 음성을 인식하는 데 적합
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // 서비스를 호출하는 앱 패키지 이름을 지정 -> 인식 결과를 올바른 앱으로 반환
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            // 반환할 최대 인식 결과 수, 가장 가능성 높은 거만
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // 최소 밀리 세컨드 이상
//            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            // 1초 정도 정적이 있으면 음성 인식을 완료됐을 가능성 있다고 판단
//            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
            // 일리 세컨드 정도 완전한 침묵 = 입력 완
//            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
        }
    }

    private fun initializeCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    // 인식 시작하기
    private fun startListening() {
        if (!isListening) {
            isListening = true
            speechRecognizer.startListening(recognizerIntent)
        }
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
        speechRecognizer.destroy() // 서비스 종료시 음성인식 종료시켜서 배터리 아끼기
    }

    // 포그라운드 서비스를 위한 알림 생성
    private fun createNotification(): Notification {
        val channelId = "OverlayServiceChannel"
        val channel =
            NotificationChannel(channelId, "Overlay Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("오버레이 서비스")
            .setContentText("오버레이 서비스가 실행 중입니다.")
            .setSmallIcon(AndroidR.drawable.ic_notification_overlay) // 적절한 아이콘으로 변경
            .build()
    }

    //    private fun createNotification(): Notification {
//        val channelId = "AudioServiceChannel"
//        val channelName = "Audio Service"
//
//        // 알림 생성 로그 추가
//        Log.d(TAG, "Creating notification")
//
//        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.createNotificationChannel(channel)
//
//        val notificationIntent = Intent(this, MainActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
//
//        return NotificationCompat.Builder(this, channelId)
//            .setContentTitle("오디오 서비스")
//            .setContentText("음성 인식 중...")
//            .setSmallIcon(AndroidR.drawable.ic_notification_overlay) // 알림 아이콘 설정, 내장 리소스(android.R)에서 이미지 가져오기
//            .setContentIntent(pendingIntent)
//            .build()
//    }
    companion object {
        private const val NOTIFICATION_ID = 1
    }
}