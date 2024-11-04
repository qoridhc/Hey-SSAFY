package com.marusys.hesap.service

import android.app.Service
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.AndroidSpeechRecognizer
import com.marusys.hesap.AudioClassifier
import com.marusys.hesap.feature.VoiceRecognizer
import com.marusys.hesap.feature.RecordRecognitionState
import com.marusys.hesap.feature.RecordStateManager

private val TAG = "AudioService"

class AudioService : Service() {
//    private var islistening  = false  // 실시간 감지 상태를 추적하는 변수
//    private lateinit var audioRecord: AudioRecord
    private lateinit var classifier: AudioClassifier  // AudioClassifier 인스턴스
    private lateinit var voiceRecognizer: VoiceRecognizer

    // 손전등 관련
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    override fun onCreate() {
        super.onCreate()
        // 카메라 초기화
        initializeCamera()
        // SpeechRecognizer 초기화
        initializeVoiceRecognizer()
        classifier = AudioClassifier(this)  // AudioClassifier 초기화
//        startForeground(NOTIFICATION_ID, createNotification()) // 포 그라운드 시작
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCommandRecognition()
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        super.onDestroy()
        RecordStateManager.updateState(RecordRecognitionState.WaitingForKeyword) // 키워드 대기상태
    }
    // 음성녹음 초기화
    private fun initializeVoiceRecognizer() {
        voiceRecognizer = AndroidSpeechRecognizer(this, object : VoiceRecognizer.Callback {
            override fun onResult(result: String) {
                executeCommand(result)
                val intent = Intent("SPEECH_RECOGNITION_RESULT")
                // 해당 intent에 result라는 이름의 String을 넣어둠
                intent.putExtra("result", result)
                // result의 값 확인하는 로그
                Log.e("들어가 있는 텍스트", result)
                // MainActivity로 결과 텍스트 보내기
                LocalBroadcastManager.getInstance(this@AudioService).sendBroadcast(intent)
            }

            override fun onError(error: String) {

            }
            override fun onPartialResult(text: String) {
//                updateNotification("인식 중: $text")
            }
        })
    }

    // 카메라 세팅 초기화
    private fun initializeCamera() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }


    private fun startCommandRecognition() {
        RecordStateManager.updateState(RecordRecognitionState.ListeningCommand)
        // 여기에 음성 명령 인식 로직 구현

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