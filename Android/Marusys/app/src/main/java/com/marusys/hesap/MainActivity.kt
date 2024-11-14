package com.marusys.hesap

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.feature.MemoryUsageManager
import com.marusys.hesap.feature.VoiceRecognitionState
import com.marusys.hesap.feature.VoiceStateManager
import com.marusys.hesap.presentation.components.AudioNotification
import com.marusys.hesap.presentation.screen.AudioScreen
import com.marusys.hesap.presentation.viewmodel.MainViewModel
import com.marusys.hesap.util.ThresholdUtil
import com.marusys.hesap.service.AudioService
import com.marusys.hesap.service.HotWordService
import com.marusys.hesap.service.NotificationService
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    // 여러 페이지에서 사용하는 값을 관리하는 viewModel
    private val mainViewModel = MainViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var memoryUsageManager: MemoryUsageManager
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1

    private lateinit var audioRecognitionServiceIntent: Intent

    // AudioService에서 인식한 음성 명령어를 MainActivity에서 받는 콜백함수
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "SPEECH_RECOGNITION_RESULT" -> {
                    val matches = intent.getStringArrayListExtra("matches")
                    val recognizedText = matches?.firstOrNull() ?: ""
                    Log.d("MainActivity", "Received text: $recognizedText")
                    // 현재 표시 중인 윈도우 매니져가 있다면 메시지 업데이트
                    mainViewModel.setCommandText(recognizedText)
                }
                "AUDIO_SERVICE_STATE_CHANGED" -> {
                    val isRunning = intent.getBooleanExtra("isRunning", false)
                    Log.d("AUDIO_SERVICE_STATE_CHANGED",isRunning.toString())
                    mainViewModel.setAudioServiceRunning(isRunning)
                }
                "RESULT_UPDATE" -> {
                    val result = intent.getStringExtra("result")
                    mainViewModel.setResultText(result ?: "")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 오디오 녹음 권한이 있는지 확인
        checkAndRequestPermissions()
        // 오버레이 권한 체크 및 요청
        checkOverlayPermission()
//        startHotWordService()
        // BroadcastReceiver 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction("SPEECH_RECOGNITION_RESULT")
                addAction("AUDIO_SERVICE_STATE_CHANGED")
                addAction("RESULT_UPDATE")
            }
        )

        // 상태 관찰, 이걸 통해 관리해도 됨
        lifecycleScope.launch {
            VoiceStateManager.voiceState.collect { state ->
                when (state) {
                    is VoiceRecognitionState.WaitingForHotword -> {
                        Log.e("","호출어 대기 상태")
//                            startRecordingWithModel()
                        startHotWordService()
                    }
                    is VoiceRecognitionState.HotwordDetecting -> {
                        Log.e("","호출어 인식 상태")
                        startAudioService()
//                        mainViewModel.setAudioServiceRunning(true)
                    }
                    is VoiceRecognitionState.CommandListening -> {
                        Log.e("","명령 들은 상태")
                    }
                    else -> {
                        // 다른 상태 처리
                    }
                }
            }
        }
        setContent {
            Surface(modifier = Modifier.fillMaxSize() ) {
                AudioScreen(
                    viewModel = mainViewModel,
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // 모든 권한이 승인된 경우
//                    startRecordingWithModel()
                    startHotWordService()
                } else {
                    // 권한이 거부된 경우
                    Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // 권한 체크 및 요청
    private fun checkAndRequestPermissions() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "오버레이 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        stopService(audioRecognitionServiceIntent)
    }
    // 호출어 인식 서비스 시작
    private fun startHotWordService() {
        audioRecognitionServiceIntent = Intent(this, HotWordService::class.java)
        startForegroundService(audioRecognitionServiceIntent)
    }
    // 로출어 인식 -> 서비스 시작
    private fun startAudioService() {
        val bundle = Bundle()
        bundle.putString("commandText", mainViewModel.commandText.value)
        bundle.putBoolean("isAudioServiceRunning", true)
        // intent AudioService로 넘기기
//        val serviceIntent = Intent(this, AudioService::class.java)
//        serviceIntent.putExtra("viewModelState", bundle)
        audioRecognitionServiceIntent = Intent(this, AudioService::class.java)
        audioRecognitionServiceIntent.putExtra("viewModelState", bundle)
        // 포그라운드 Service 시작
//        ContextCompat.startForegroundService(this, serviceIntent)
//        VoiceStateManager.updateState(VoiceRecognitionState.HotwordDetecting) // 호출어 인식 완료, isListen = false
//        startForegroundService(audioRecognitionServiceIntent)
        startService(audioRecognitionServiceIntent)
    }

    // 서비스 종료
    private fun stopAudioService() {
        // 배경 변경
        mainViewModel.setAudioServiceRunning(false)
        val serviceIntent = Intent(this, AudioService::class.java)
        stopService(serviceIntent)
        VoiceStateManager.updateState(VoiceRecognitionState.WaitingForHotword) // 호출어 대기
    }

    // ======== 음성 인식 기반 분류 ========
//    fun cnnRealTimeRecordAndClassify() {
//        val bufferSize = AudioRecord.getMinBufferSize(
//            SAMPLE_RATE,
//            AudioFormat.CHANNEL_IN_MONO,
//            AudioFormat.ENCODING_PCM_16BIT
//        ) * RECORDING_TIME
//
//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.RECORD_AUDIO
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            return
//        }
//
//        // 상태 표시
//        runOnUiThread {
//            mainViewModel.setResultText("듣고 있는 중이에요.")
//        }
//
//        Thread {
//            val audioRecord = AudioRecord(
//                MediaRecorder.AudioSource.MIC,
//                SAMPLE_RATE,
//                AudioFormat.CHANNEL_IN_MONO,
//                AudioFormat.ENCODING_PCM_16BIT,
//                bufferSize
//            )
//            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
//                Log.e("MainActivity", "AudioRecord 초기화 실패")
//                mainViewModel.setResultText("녹음 초기화 실패")
//                return@Thread
//            }
//
//            val audioBuffer = ShortArray(bufferSize / 2)
//            val slidingWindowBuffer = FloatArray(WINDOW_SIZE)  // 1초 버퍼
//            var bufferPosition = 0
//
//            audioRecord.startRecording()
//
//            // 실시간으로 데이터를 읽어들여 모델로 전달
//            while (VoiceStateManager.voiceState.value == VoiceRecognitionState.WaitingForHotword) {
//                val readSize = audioRecord.read(audioBuffer, 0, audioBuffer.size)
//                if (readSize > 0) {
//                    for (i in 0 until readSize) {
//                        slidingWindowBuffer[bufferPosition] = audioBuffer[i] / 32768.0f
//                        bufferPosition++
//
//                        // 슬라이딩 윈도우가 채워졌으면 호출어 검출을 수행
//                        if (bufferPosition >= WINDOW_SIZE) {
//                            bufferPosition = 0
//
//                            try {
//                                val classifier = AudioClassifier(this)
//                                val inputBuffer = classifier.createInputBuffer(slidingWindowBuffer)
//                                val results = classifier.classify(inputBuffer)
//
//                                // results[0] 값을 실시간으로 화면에 표시
//                                runOnUiThread {
//                                    val percentage = String.format("%.2f%%", results[0] * 100)
//                                    mainViewModel.setResultText("확률값: $percentage")
//                                }
//
//                                // 호출어가 감지되면 팝업을 띄우고 스레드를 중단
//                                if (results[0] >= THRESHOLD) {
//                                    runOnUiThread {
//                                        startAudioService() // AudioService 시작
//                                    }
//                                    break  // 루프 종료
//                                }
//                            } catch (e: Exception) {
//                                Log.e("MainActivity", "분류 중 오류 발생", e)
//                                runOnUiThread {
//                                    mainViewModel.setResultText("분류 중 오류가 발생했습니다: " + e.message)
//                                }
//                            }
//                            // 슬라이딩 윈도우를 50% 이동시키기 위해 이전 데이터를 복사
//                            System.arraycopy(
//                                slidingWindowBuffer,
//                                STEP_SIZE,
//                                slidingWindowBuffer,
//                                0,
//                                WINDOW_SIZE - STEP_SIZE
//                            )
//                            bufferPosition = WINDOW_SIZE - STEP_SIZE
//                        }
//                    }
//                }
//            }
//            audioRecord.stop()
//            audioRecord.release()
//        }.start()
//    }



//    fun resnetRealTimeRecordAndClassify() {
//
//        val bufferSize = AudioRecord.getMinBufferSize(
//            SAMPLE_RATE,
//            AudioFormat.CHANNEL_IN_MONO,
//            AudioFormat.ENCODING_PCM_16BIT
//        ) * RECORDING_TIME
//
//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.RECORD_AUDIO
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            return
//        }
//
//        // 상태 표시
//        runOnUiThread {
//            mainViewModel.setResultText("듣고 있는 중이에요.")
//        }
//
//        Thread {
//            val audioRecord = AudioRecord(
//                MediaRecorder.AudioSource.MIC,
//                SAMPLE_RATE,
//                AudioFormat.CHANNEL_IN_MONO,
//                AudioFormat.ENCODING_PCM_16BIT,
//                bufferSize
//            )
//            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
//                Log.e("MainActivity", "AudioRecord 초기화 실패")
//                runOnUiThread {
//                    mainViewModel.setResultText("녹음 초기화 실패")
//                }
//                return@Thread
//            }
//
//            val audioBuffer = ShortArray(bufferSize / 2)
//            val slidingWindowBuffer = FloatArray(WINDOW_SIZE)
//            var bufferPosition = 0
//
//            audioRecord.startRecording()
//
//            // 실시간으로 데이터를 읽어들여 모델로 전달
//            while (VoiceStateManager.voiceState.value == VoiceRecognitionState.WaitingForHotword) {
//                val readSize = audioRecord.read(audioBuffer, 0, audioBuffer.size)
//                if (readSize > 0) {
//                    for (i in 0 until readSize) {
//                        slidingWindowBuffer[bufferPosition] = audioBuffer[i] / 32768.0f
//                        bufferPosition++
//
//                        // 슬라이딩 윈도우가 채워졌으면 호출어 검출을 수행
//                        if (bufferPosition >= WINDOW_SIZE) {
//                            bufferPosition = 0
//                            try {
//                                val classifier = ResnetClassifier(this)
//                                val results = classifier.classifyAudio(slidingWindowBuffer)
//                                val accuracy = ThresholdUtil.checkTrigger(results)
//                                val resultLabel = classifier.getLabel(results)
//
//                                val resultText = StringBuilder()
//                                val percentage = String.format("%.2f%%", accuracy * 100)
//
//                                resultText.append(classifier.getLabel(results))
//                                resultText.append(" : ")
//                                resultText.append(percentage)
//
//                                val finalResult = resultText.toString()
//
//                                // 정확도 값을 실시간으로 화면에 표시
//                                runOnUiThread {
//                                    mainViewModel.setResultText(finalResult)
//                                }
//
//                                // 호출어가 감지되면 팝업을 띄우고 스레드를 중단
//                                if (accuracy >= THRESHOLD && resultLabel.equals(TRIGGER_WORD)) {
//                                    runOnUiThread {
//                                        startAudioService() // AudioService 시작
//                                    }
//                                    break  // 루프 종료
//                                }
//                            } catch (e: Exception) {
//                                Log.e("MainActivity", "분류 중 오류 발생", e)
//                                runOnUiThread {
//                                    mainViewModel.setResultText("분류 중 오류가 발생했습니다: " + e.message)
//                                }
//                            }
//
//                            // 슬라이딩 윈도우를 50% 이동시키기 위해 이전 데이터를 복사
//                            System.arraycopy(
//                                slidingWindowBuffer,
//                                STEP_SIZE,
//                                slidingWindowBuffer,
//                                0,
//                                WINDOW_SIZE - STEP_SIZE
//                            )
//                            bufferPosition = WINDOW_SIZE - STEP_SIZE
//                        }
//                    }
//                }
//            }
//            audioRecord.stop()
//            audioRecord.release()
//        }.start()
//    }
}



