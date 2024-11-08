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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import com.marusys.hesap.MainActivity.Constants.RECORDING_TIME
import com.marusys.hesap.MainActivity.Constants.SAMPLE_RATE
import com.marusys.hesap.MainActivity.Constants.STEP_SIZE
import com.marusys.hesap.MainActivity.Constants.THRESHOLD
import com.marusys.hesap.MainActivity.Constants.TRIGGER_WORD
import com.marusys.hesap.MainActivity.Constants.WINDOW_SIZE
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.feature.MemoryUsageManager
import com.marusys.hesap.feature.VoiceRecognitionState
import com.marusys.hesap.feature.VoiceStateManager
import com.marusys.hesap.presentation.screen.AudioScreen
import com.marusys.hesap.presentation.viewmodel.MainViewModel
import com.marusys.hesap.util.ThresholdUtil
import com.marusys.hesap.service.AudioService
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    // 여러 페이지에서 사용하는 값을 관리하는 viewModel
    private val mainViewModel = MainViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var memoryUsageManager: MemoryUsageManager
    private var currentDialog: AlertDialog? = null
    private var isListening = false

    // 모델 타입
    enum class ModelType {
        RESNET, CNN, GRU
    }

    var MODEL_TYPE: ModelType = ModelType.GRU

    object Constants {
        // 녹음 관련 설정
        const val THRESHOLD = 0.95
        const val SAMPLE_RATE = 16000   // 샘플 레이트 16KHz (16000Hz)
        const val RECORDING_TIME = 2    // 녹음 시간 (2초)
        const val WINDOW_SIZE = SAMPLE_RATE * RECORDING_TIME  // 전체 window size
        const val STEP_SIZE = SAMPLE_RATE / 2     // sliding window 사이즈 (겹치는 구간)

        // Resnet Softmax 분류를 위한 트리거워드 설정
        const val TRIGGER_WORD = "hey_ssafy"

        // 라벨 정의 (모델 학습 시 사용한 라벨에 맞게 수정)
        val LABELS = arrayOf("unknown", "ssafy")
    }

    // AudioService에서 인식한 음성 명령어를 MainActivity에서 받는 콜백함수
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SPEECH_RECOGNITION_RESULT") {
                val matches = intent.getStringArrayListExtra("matches")
                val recognizedText = matches?.firstOrNull() ?: ""
                Log.d("MainActivity", "Received text: $recognizedText")

                // 현재 표시 중인 다이얼로그가 있다면 메시지 업데이트
                currentDialog?.setMessage(recognizedText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        // ViewModel의 메모리 사용량 업데이트 시작
        mainViewModel.updateMemoryUsage(this)

        // BroadcastReceiver 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter("SPEECH_RECOGNITION_RESULT")
        )
        // 상태 관찰, 이걸 통해 관리해도 됨
        lifecycleScope.launch {
            VoiceStateManager.voiceState.collect { state ->
                when (state) {
                    is VoiceRecognitionState.WaitingForHotword -> {
                        Log.e("","호출어 대기 상태")
                        Log.e("","isListening = $isListening")
                        if (!isListening) {
                            isListening = true
                            Log.e("","lifecycle 에서 startRecord 하기")
                            startRecordingWithModel()
                        }
                    }
                    is VoiceRecognitionState.HotwordDetecting -> {
                        Log.e("","호출어 인식 상태")
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
            AudioScreen(viewModel = mainViewModel)
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
                    startRecordingWithModel()
                } else {
                    // 권한이 거부된 경우
                    Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                    // 필요에 따라 사용자에게 권한의 필요성을 설명하고 다시 요청하거나 앱을 종료할 수 있습니다.
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        currentDialog?.dismiss()
        currentDialog = null
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

    // 사용자에게 모델 타입을 선택할 수 있게 해주는 메서드
    private fun startRecordingWithModel() {
        when (MODEL_TYPE) {
            ModelType.RESNET -> resnetRealTimeRecordAndClassify()
            ModelType.CNN -> cnnRealTimeRecordAndClassify()
            ModelType.GRU -> gruRealTimeRecordAndClassify()
        }
    }

    // ======== 음성 인식 기반 분류 ========
    fun cnnRealTimeRecordAndClassify() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * RECORDING_TIME

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
            //VoiceStateManager.updateState(VoiceRecognitionState.WaitingForHotword) // 스레드 실행 시작 isListening = true
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("MainActivity", "AudioRecord 초기화 실패")
                mainViewModel.setResultText("녹음 초기화 실패")
                return@Thread
            }

            val audioBuffer = ShortArray(bufferSize / 2)
            val slidingWindowBuffer = FloatArray(WINDOW_SIZE)  // 1초 버퍼
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
                        if (bufferPosition >= WINDOW_SIZE) {
//                            bufferPosition = 0
                            try {
//                                val audioData = captureAudio() // 오디오 캡처 함수
                                val classifier = AudioClassifier(this)
                                val inputBuffer = classifier.createInputBuffer(slidingWindowBuffer)
                                val results = classifier.classify(inputBuffer)
//                                val results = audioClassifier.classify(slidingWindowBuffer)
                                // results[0] 값을 실시간으로 화면에 표시
                                runOnUiThread {
                                    val percentage = String.format("%.2f%%", results[0] * 100)
                                    mainViewModel.setResultText("확률값: $percentage")
                                }

                                // 호출어가 감지되면 팝업을 띄우고 스레드를 중단
                                if (results[0] >= THRESHOLD) {
                                    runOnUiThread {
                                        if (currentDialog == null) { showSuccessDialog() } // dialog 창 오픈
                                        // 호출어 감지 -> AudioService 시작
                                        startAudioService() // 서비스 시작
                                    }
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
                                STEP_SIZE,
                                slidingWindowBuffer,
                                0,
                                WINDOW_SIZE - STEP_SIZE
                            )
                            bufferPosition = WINDOW_SIZE - STEP_SIZE
                        }
                    }
                }
            }
            audioRecord.stop()
            audioRecord.release()
        }.start()
    }

    fun gruRealTimeRecordAndClassify() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * RECORDING_TIME

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
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
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
            val slidingWindowBuffer = FloatArray(WINDOW_SIZE)  // 1초 버퍼
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
                        if (bufferPosition >= WINDOW_SIZE) {
                            bufferPosition = 0

                            try {
                                val classifier = AudioClassifier(this)
                                val results = classifier.classify(slidingWindowBuffer)

                                // results[0] 값을 실시간으로 화면에 표시
                                runOnUiThread {
                                    val percentage = String.format("%.2f%%", results[0] * 100)

                                    mainViewModel.setResultText("확률값: $percentage")
                                }

                                // 호출어가 감지되면 팝업을 띄우고 스레드를 중단
                                if (results[0] >= THRESHOLD) {
                                    runOnUiThread {
                                        if (currentDialog == null) { showSuccessDialog() } // dialog 창 오픈
                                        // 호출어 감지 -> AudioService 시작
                                        startAudioService() // 서비스 시작
                                    }
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
                                STEP_SIZE,
                                slidingWindowBuffer,
                                0,
                                WINDOW_SIZE - STEP_SIZE
                            )
                            bufferPosition = WINDOW_SIZE - STEP_SIZE
                        }
                    }
                }
            }
            audioRecord.stop()
            audioRecord.release()
        }.start()
    }

    fun resnetRealTimeRecordAndClassify() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * RECORDING_TIME

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
//            VoiceStateManager.updateState(VoiceRecognitionState.WaitingForHotword) // 스레드 실행 시작 isListening = true
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
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
            val slidingWindowBuffer = FloatArray(WINDOW_SIZE)
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
                        if (bufferPosition >= WINDOW_SIZE) {
                            bufferPosition = 0

                            try {
                                val classifier = ResnetClassifier(this)
                                val results = classifier.classifyAudio(slidingWindowBuffer)
                                val accuracy = ThresholdUtil.checkTrigger(results)
                                val resultLabel = classifier.getLabel(results)


                                val resultText = StringBuilder()
                                val percentage = String.format("%.2f%%", accuracy * 100)

                                resultText.append(classifier.getLabel(results))
                                resultText.append(" : ")
                                resultText.append(percentage)

                                val finalResult = resultText.toString()

                                // 정확도 값을 실시간으로 화면에 표시
                                runOnUiThread {
                                    mainViewModel.setResultText(finalResult)
                                }

                                // 호출어가 감지되면 팝업을 띄우고 스레드를 중단
                                if (accuracy >= THRESHOLD && resultLabel.equals(TRIGGER_WORD)) {
                                    runOnUiThread {
                                        if (currentDialog == null) { showSuccessDialog() } // dialog 창 오픈
                                        startAudioService()
                                    }
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
                                STEP_SIZE,
                                slidingWindowBuffer,
                                0,
                                WINDOW_SIZE - STEP_SIZE
                            )
                            bufferPosition = WINDOW_SIZE - STEP_SIZE
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
        VoiceStateManager.updateState(VoiceRecognitionState.HotwordDetecting) // 호출어 인식 완료, isListen = false
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
            .setMessage("호출어가 성공적으로 인식되었습니다!")
            .setPositiveButton("확인") { it, _ ->
                Log.e("","확인 버튼 눌렀음")
                it.dismiss()
                isListening = false
                stopAudioService() // 서비스 종료
                currentDialog = null
                Log.e("", " 확인 버튼 눌렀을 때 : $isListening")
                VoiceStateManager.updateState(VoiceRecognitionState.WaitingForHotword)
//                startRecordingWithModel()  // 스레드 재시작
            }
            .setCancelable(false)
            .create()
        currentDialog = dialog
        currentDialog?.show()
    }
}



