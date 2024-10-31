package com.marusys.hesap.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.AudioClassifier
import com.marusys.hesap.MainActivity
import com.marusys.hesap.R

private val TAG = "AudioService"
class AudioService : Service() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var audioRecord: AudioRecord
    private var isHotwordDetectionActive = false
    private var isFullRecognitionActive = false
    private lateinit var recognizerIntent: Intent
    private lateinit var cameraManager: CameraManager
    private val handler = Handler(Looper.getMainLooper())
    private var cameraId: String? = null
    private var isListening = false // 음성 인식 상태를 추적하는 변수 추가
    // 임의 추가
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    // AudioClassifier
    private var isRealTimeListening = false  // 실시간 감지 상태를 추적하는 변수
    private lateinit var classifier: AudioClassifier  // AudioClassifier 인스턴스

    private val bufferSize = AudioRecord.getMinBufferSize(
        16000,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    override fun onCreate() {
        super.onCreate()
        initializeCamera()
        initializeSpeechRecognizer()
        classifier = AudioClassifier(this)  // AudioClassifier 초기화
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationBuilder = createNotificationBuilder()
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        createNotificationChannel()
        startHotwordDetection()
    }
    // 음성녹음 초기화
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
    // 카메라 세팅 초기화
    private fun initializeCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }
    // 손전등 on off
    private fun toggleFlashlight(on: Boolean) {
        cameraId?.let { id ->
            cameraManager.setTorchMode(id, on)
        }
    }
    // 핫워드 인식
    @SuppressLint("MissingPermission") //퍼미션 요구 무시하기
    private fun startHotwordDetection() {
        if (isHotwordDetectionActive) return

        isHotwordDetectionActive = true
        Thread {
            val buffer = ShortArray(bufferSize)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord.startRecording()

            while (isHotwordDetectionActive) {
                val readSize = audioRecord.read(buffer, 0, bufferSize)
                if (readSize > 0) {
                    // 여기서 기존 AI 모델로 호출어 감지
                    val isHotwordDetected = checkHotword(buffer)
                    if (isHotwordDetected) {
//                        startFullSpeechRecognition()
                        Log.e(TAG,"111111111111111111111111111111111")
                        startRealTimeRecording()
                    }
                }
                // 전력 소비 감소를 위한 짧은 대기
                Thread.sleep(10)
            }
        }.start()
    }

    private fun startFullSpeechRecognition() {
        if (isFullRecognitionActive) return

        isFullRecognitionActive = true
        updateNotification("음성 명령을 기다리는 중...")

        speechRecognizer.startListening(recognizerIntent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { result ->
                updateNotification("인식 중: $result")
            }
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { result ->
                handleVoiceCommand(result)
            }
            val intent = Intent("SPEECH_RECOGNITION_RESULT")
            // 해당 intent에 matches라는 이름의 matches 결과 배열 리스트를 넣어둠
            intent.putStringArrayListExtra("matches", matches)
            // matches의 값 확인하는 로그
            Log.e("들어가 있는 텍스트", "$matches")
            // 명령 처리 후 다시 호출어 감지 모드로 전환
            isFullRecognitionActive = false
            updateNotification("호출어 대기 중...")
            LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(intent)
        }
        override fun onError(error: Int) {
            isFullRecognitionActive = false
            updateNotification("호출어 대기 중...")
        }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            //입력되는 데시벨 크기를 상수로
            // Log.d("sound","$rmsdB");
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

    }
    private fun updateNotification(text: String) {
        notificationBuilder.setContentText(text)
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }
    private fun handleVoiceCommand(command: String) {
        when {
            command.contains("손전등 켜", ignoreCase = true) -> toggleFlashlight(true)
            command.contains("손전등 꺼", ignoreCase = true) -> toggleFlashlight(false)
            command.contains("헤이 사피", ignoreCase = true) -> {
                val intent = Intent(this@AudioService, OverlayService::class.java)
                intent.action = "SHOW_OVERLAY"
                startService(intent) // Overlay Service 시작
            }
        }
    }

    private fun checkHotword(audioData: ShortArray): Boolean {
        // 여기에 기존 AI 모델을 사용한 호출어 감지 로직 구현
        return false // 임시 반환값
    }
    // 포그라운드 서비스를 위한 알림 생성
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "AudioServiceChannel",
            "Audio Service",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
    private fun createNotificationBuilder(): NotificationCompat.Builder {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }
        return NotificationCompat.Builder(this, "AudioServiceChannel")
            .setContentTitle("마르시스")
            .setContentText("음성 인식 대기 중...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
    }

    private fun startRealTimeRecording() {
        val sampleRate = 16000
        val windowSize = 16000  // 1초 분량의 샘플
        val stepSize = 8000     // 0.5초 분량의 샘플

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // 알림 업데이트
        updateNotification("호출어 감지 중...")

        Thread {
            isRealTimeListening = true
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 초기화 실패")
                updateNotification("녹음 초기화 실패")
                return@Thread
            }

            val audioBuffer = ShortArray(bufferSize / 2)
            val slidingWindowBuffer = FloatArray(windowSize)
            var bufferPosition = 0

            audioRecord.startRecording()

            while (isRealTimeListening) {
                val readSize = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                if (readSize > 0) {
                    for (i in 0 until readSize) {
                        slidingWindowBuffer[bufferPosition] = audioBuffer[i] / 32768.0f
                        bufferPosition++

                        if (bufferPosition >= windowSize) {
                            bufferPosition = 0

                            try {
                                val inputBuffer = classifier.createInputBuffer(slidingWindowBuffer)
                                val results = classifier.classify(inputBuffer)

                                // 결과를 브로드캐스트로 전송
                                sendClassificationResult(results[0])

                                // 호출어가 감지되면
                                if (results[0] >= 0.9f) {
                                    // 전체 음성 인식 모드로 전환
                                    isRealTimeListening = false
                                    speechRecognizer.startListening(recognizerIntent)
                                    break
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "분류 중 오류 발생", e)
                                updateNotification("분류 중 오류 발생")
                            }

                            // 슬라이딩 윈도우 이동
                            System.arraycopy(
                                slidingWindowBuffer,
                                stepSize,
                                slidingWindowBuffer,
                                0,
                                windowSize - stepSize
                            )
                            bufferPosition = windowSize - stepSize
                        }
                    }
                }
            }
            audioRecord.stop()
            audioRecord.release()
        }.start()
    }
    private fun sendClassificationResult(probability: Float) {
        val intent = Intent("CLASSIFICATION_RESULT")
        intent.putExtra("probability", probability)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRealTimeListening = false // classifier
        isHotwordDetectionActive = false
        audioRecord.stop()
        audioRecord.release()
        speechRecognizer.destroy()
    }
    companion object {
        private const val NOTIFICATION_ID = 1
    }
}