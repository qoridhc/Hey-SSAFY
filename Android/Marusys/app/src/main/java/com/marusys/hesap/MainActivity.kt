package com.marusys.hesap

import android.Manifest
import android.app.AlertDialog
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
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.feature.MemoryUsageManager
import com.marusys.hesap.feature.VoiceRecognitionState
import com.marusys.hesap.feature.VoiceStateManager
import com.marusys.hesap.presentation.screen.AudioScreen
import com.marusys.hesap.presentation.viewmodel.MainViewModel
import com.marusys.hesap.service.AudioService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var memoryUsageManager: MemoryUsageManager
//    private var currentDialog: AlertDialog? = null
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1

    // AudioService에서 방송하는걸 MainActivity에서 받아서 객체? (정확한 명칭 모름) 로 정의
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SPEECH_RECOGNITION_RESULT") {
                val matches = intent.getStringArrayListExtra("matches")
                val recognizedText = matches?.firstOrNull() ?: ""
                Log.d("MainActivity", "Received text: $recognizedText")

                // 현재 표시 중인 다이얼로그가 있다면 메시지 업데이트
//                currentDialog?.setMessage(recognizedText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 오디오 녹음 권한이 있는지 확인
        checkAndRequestPermissions()
        // 오버레이 권한 체크 및 요청
        checkOverlayPermission()
        // ViewModel의 메모리 사용량 업데이트 시작
        mainViewModel.updateMemoryUsage(this)

        // BroadcastReceiver 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter("SPEECH_RECOGNITION_RESULT")
        )
        // 상태 관찰
        lifecycleScope.launch {
            VoiceStateManager.voiceState.collect { state ->
                when (state) {
                    is VoiceRecognitionState.WaitingForHotword -> {
//                        isListening = true
                        realTimeRecordAndClassify()
                    }

                    else -> {
                        // 다른 상태 처리
                    }
                }
            }
        }
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
            AudioScreen(
                viewModel = mainViewModel,
            )
            }
        }
    }

    private fun initializeMemoryUsageManager() {
        memoryUsageManager = MemoryUsageManager()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        } else {
//            val intent = Intent(this, OverlayService::class.java).apply {
//                action = "SHOW_OVERLAY"
//            }
//            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면 갔다가 돌아왔을 때 받기위함
        realTimeRecordAndClassify()
    }

    override fun onPause() {
        super.onPause()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
//                val intent = Intent(this, OverlayService::class.java).apply {
//                    action = "SHOW_OVERLAY"
//                }
//                startService(intent)
            } else {
                // 사용자가 권한을 거부했을 때의 처리
                Toast.makeText(this, "오버레이 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
//        currentDialog?.dismiss()
//        currentDialog = null
    }

    // 권한 체크 및 요청
    private fun checkAndRequestPermissions() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA),
                1
            )
        }
    }
    // ======== 음성 인식 기반 분류 ========

    // 호출어 인식 여부에 따라 스레드 일시 중단 시키기 위한 변수
//    private var isListening = false // 위로 이동

    private fun realTimeRecordAndClassify() {
        val sampleRate = 16000
        val windowSize = 32000  // 2초 분량의 샘플 (32000개)
        val stepSize = 8000     // 0.5초 분량의 샘플 (겹치는 구간)

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

        // 상태 표시
        runOnUiThread {
            mainViewModel.setResultText("녹음 중...")
        }

        Thread {
//            isListening = true  // 스레드 실행 시작
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("MainActivity", "AudioRecord 초기화 실패")
                runOnUiThread {
                    mainViewModel.setResultText("녹음 초기화 실패")
                }
                return@Thread
            }

            val audioBuffer = ShortArray(bufferSize / 2)
            val slidingWindowBuffer = FloatArray(windowSize)  // 1초 버퍼
            var bufferPosition = 0

            audioRecord.startRecording()

            // 실시간으로 데이터를 읽어들여 모델로 전달
            while (VoiceStateManager.voiceState.value == VoiceRecognitionState.WaitingForHotword) {
                val readSize = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                if (readSize > 0) {
                    for (i in 0 until readSize) {
                        slidingWindowBuffer[bufferPosition] = audioBuffer[i] / 32768.0f
                        bufferPosition++

                        // 슬라이딩 윈도우가 채워졌으면 호출어 검출을 수행
                        if (bufferPosition >= windowSize) {
                            bufferPosition = 0

                            try {
//                                val audioData = captureAudio() // 오디오 캡처 함수
                                val classifier = AudioClassifier(this)
                                val inputBuffer = classifier.createInputBuffer(slidingWindowBuffer)
                                val results = classifier.classify(inputBuffer)

                                // results[0] 값을 실시간으로 화면에 표시
                                runOnUiThread {
                                    val percentage = String.format("%.2f%%", results[0] * 100)

                                    mainViewModel.setResultText("확률값: $percentage")
                                }

                                // 호출어가 감지되면 팝업을 띄우고 스레드를 중단
                                if (results[0] >= 0.8f) {
                                    runOnUiThread {
                                        // 호출어 감지 -> AudioService 시작
                                        startAudioService() // 서비스 시작
//                                        if (currentDialog == null){ showSuccessDialog()} // dialog 창 오픈
//                                        val overlayIntent = Intent(this, OverlayService::class.java)
//                                        overlayIntent.action = "SHOW_OVERLAY"
//                                        overlayIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//                                        startService(overlayIntent)
                                    }
                                    VoiceStateManager.updateState(VoiceRecognitionState.HotwordDetecting)
                                    break  // 루프 종료
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "분류 중 오류 발생", e)
                                runOnUiThread {
                                    mainViewModel.setResultText("분류 중 오류가 발생했습니다: " + e.message)
                                }
                            }

                            // 슬라이딩 윈도우를 50% 이동시키기 위해 이전 데이터를 복사
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

    // 서비스 시작
    private fun startAudioService() {
        val serviceIntent = Intent(this, AudioService::class.java)
        // 포그라운드 Service 시작
//        ContextCompat.startForegroundService(this, serviceIntent)
        startService(serviceIntent)
    }

    // 서비스 종료
    private fun stopAudioService() {
        val serviceIntent = Intent(this, AudioService::class.java)
        stopService(serviceIntent)
    }
    // 호출어 인식 성공 시 보여줄 팝업
    private fun showSuccessDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("호출어 인식 성공")
            .setMessage("")
            .setPositiveButton("확인") { it, _ ->
                it.dismiss()
                stopAudioService() // 서비스 종료
                VoiceStateManager.updateState(VoiceRecognitionState.WaitingForHotword)
            }
            .setCancelable(true)
            .create()

        // 다이얼로그 참조 저장
//            currentDialog = dialog

       dialog.setOnDismissListener {
//            currentDialog = null
           stopAudioService()
        }

        dialog.show()
    }
}



