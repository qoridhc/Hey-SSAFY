package com.marusys.hesap

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.feature.MemoryUsageManager
import com.marusys.hesap.presentation.screen.AudioScreen
import com.marusys.hesap.presentation.viewmodel.MainViewModel
import com.marusys.hesap.service.AudioService
import kotlin.math.exp

class MainActivity : ComponentActivity() {
    // 여러 페이지에서 사용하는 값을 관리하는 viewModel
    private val mainViewModel = MainViewModel()
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var memoryUsageManager: MemoryUsageManager
    // 분류할 라벨들 -> 모델 학습 시 사용한 라벨
    private val labels = arrayOf(
        "down",
        "go",
        "left",
        "no",
        "right",
        "stop",
        "up",
        "yes"
    )
    // AudioService에서 방송하는걸 MainActivity에서 받아서 객체? (정확한 명칭 모름) 로 정의
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SPEECH_RECOGNITION_RESULT") {
                val matches = intent.getStringArrayListExtra("matches")
                mainViewModel.setResultText(matches?.firstOrNull() ?: "")
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 권한 체크 및 요청
        checkAndRequestPermissions()

        // 오버레이 권한 체크 및 요청
        checkAndRequestOverlayPermission()

        // AudioService 시작
        startAudioService()

        // BroadcastReceiver 등록
        registerBroadcastReceiver()

        // 메모리 사용량 관리자 초기화
        initializeMemoryUsageManager()

        // UI 설정
        setContent {
            AudioScreen(
                viewModel = mainViewModel,
                recordButtons = { recordAndClassify() },
                startService = { startAudioService() },
            )
        }
    }
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
    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }
    private fun startAudioService() {
        val serviceIntent = Intent(this, AudioService::class.java)
        // 포그라운드 서비스 시작
        ContextCompat.startForegroundService(this, serviceIntent)
    }
    private fun registerBroadcastReceiver() {
        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter("SPEECH_RECOGNITION_RESULT")
        )
    }
    private val updateMemoryRunnable = object : Runnable {
        override fun run() {
//            mainViewModel.setMemoryText(memoryUsageManager.getMemoryUsage())
            mainViewModel.setMemoryText(getMemoryUsage())
            handler.postDelayed(this, 1000) // 1초마다 업데이트
        }
    }
    private fun initializeMemoryUsageManager() {
        memoryUsageManager = MemoryUsageManager(this)
    }
    // 메모리 사용량 보는 테스트용 코드
    fun getMemoryUsage(): String {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val pid = Process.myPid()
        val pInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))[0]

        val totalPss = pInfo.totalPss / 1024 // KB to MB
        val privateDirty = pInfo.totalPrivateDirty / 1024 // KB to MB

        return "현재 앱 메모리 사용량:\n" +
                "앱이 사용하는 총 메모리양, 공유 메모리 포함: $totalPss MB\n" +
                "앱이 독점적으로 사용하는 메모리양: $privateDirty MB"
    }
    override fun onResume() {
        super.onResume()
        handler.post(updateMemoryRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateMemoryRunnable)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, AudioService::class.java))
            } else {
                Toast.makeText(this, "오버레이 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }
    // ======== 음성 인식 기반 분류 ========
    // 현재는 버튼 리스너 기반 -> 추후에 실시간 음성인식 코드 구현
    fun recordAndClassify() {
        // 샘플 레이트 16KHz(16000Hz)
        val sampleRate = 16000
        // 녹음 설정 시간 ( 1초로 설정 )
        val recordingTime = 1
        // 샘플 갯수 계산
        val totalSamples = sampleRate * recordingTime
        // 최소 버퍼 크기를 얻어옴
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // 녹음 권한이 있는지 재확인
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // 녹음 상태 표시해주기 (스레드 별도 설정)
        runOnUiThread {
            mainViewModel.setResultText("녹음 중...")
        }

        // 백그라운드 스레드에서 녹음 및 분류 실행
        Thread(Runnable {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,  // 마이크에서 오디오 소스 가져옴
                sampleRate,  // 샘플레이트 설정 (16kHz)
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize // 버퍼 크기 설정
            )
            // AudioRecord 초기화 실패 시 로그 출력 및 종료
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("MainActivity", "AudioRecord 초기화 실패")
                runOnUiThread {
                    mainViewModel.setResultText("녹음 초기화 실패")
                }
                return@Runnable
            }

            // 녹음할 샘플을 저장할 버퍼 생성
            val audioBuffer = ShortArray(totalSamples)

            // 녹음 시작
            audioRecord.startRecording()

            // 녹음 데이터 읽기
            audioRecord.read(audioBuffer, 0, audioBuffer.size)

            // 녹음 종료 & 리소스 해제
            audioRecord.stop()
            audioRecord.release()

            Log.d("원시 데이터", audioBuffer.contentToString())

            // short 배열을 float 배열로 변환 (정규화 포함)
            val audioData = FloatArray(16000)
            for (i in audioData.indices) {
                // 16비트 데이터 정규화 (-1.0 ~ 1.0 값으로 맞춰줌)
                audioData[i] = audioBuffer[i] / 32768.0f
            }

            // 입력 음성 데이터 값 로그
            Log.d("audioData", audioData.contentToString())
            try {
                // 자체 AudioClassifier를 사용하여 분류
                val classifier = AudioClassifier(this)

                // 입력 데이터를 분류 모델의 형식에 맞게 변환
                val inputBuffer = classifier.createInputBuffer(audioData)
                val results = classifier.classify(inputBuffer)

                // 소프트맥스 적용
                var max = Float.NEGATIVE_INFINITY
                for (result in results) {
                    if (result > max) max = result
                }

                var sum = 0f
                val softmaxResults = FloatArray(results.size)

                for (i in results.indices) {
                    softmaxResults[i] = exp((results[i] - max).toDouble()).toFloat()
                    sum += softmaxResults[i]
                }

                // 소프트맥스 결과 정규화
                for (i in softmaxResults.indices) {
                    softmaxResults[i] /= sum
                }

                // 결과 문자열 생성 (결과 값 포맷팅)
                val resultText = StringBuilder()
                for (i in results.indices) {
                    resultText.append(labels[i])
                        .append(" : ")
                        .append(String.format("%.4f", results[i]))
                        .append("(")
                        .append(String.format("%.2f", softmaxResults[i] * 100))
                        .append("%)\n")
                }

                val finalResult = resultText.toString()

                // 결과 출력
                runOnUiThread {
                    mainViewModel.setResultText(finalResult)
                }
                Log.d("resultText", finalResult)
            } catch (e: Exception) {
                Log.e("MainActivity", "분류 중 오류 발생", e)
                runOnUiThread {
                    mainViewModel.setResultText("분류 중 오류가 발생했습니다: " + e.message)
                }
            }
        }).start()
    } // ======== wav 파일 기반 분류 ========
}



