package com.marusys.hesap

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import com.marusys.hesap.feature.MemoryUsageManager
import com.marusys.hesap.presentation.screen.AudioScreen
import com.marusys.hesap.presentation.viewmodel.MainViewModel
import java.util.Arrays
import java.util.concurrent.LinkedBlockingQueue

class MainActivity : ComponentActivity() {
    // 여러 페이지에서 사용하는 값을 관리하는 viewModel
    private val mainViewModel = MainViewModel()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var memoryUsageManager: MemoryUsageManager

    // 분류할 라벨들 -> 모델 학습 시 사용한 라벨

    private val labels = arrayOf(
        "unknown",
        "ssafy"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 오디오 녹음 권한이 있는지 확인
        checkAndRequestPermissions()
        // 메모리 사용량 관리자 초기화
        initializeMemoryUsageManager()
        setContent {
            AudioScreen(
                viewModel = mainViewModel,
                recordButtons = {}
            )
        }
    }

    private val updateMemoryRunnable = object : Runnable {
        override fun run() {
            mainViewModel.setMemoryText(memoryUsageManager.getMemoryUsage())
            handler.postDelayed(this, 1000) // 1초마다 업데이트
        }
    }

    private fun initializeMemoryUsageManager() {
        memoryUsageManager = MemoryUsageManager(this)
    }

    override fun onResume() {
        super.onResume()
        // 화면 갔다가 돌아왔을 때 받기위함
        realTimeRecordAndClassify()
        handler.post(updateMemoryRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateMemoryRunnable)
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
    private var isListening = false

    fun realTimeRecordAndClassify() {
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

        isListening = true  // 스레드 실행 시작
        val audioQueue = LinkedBlockingQueue<FloatArray>()  // 슬라이딩 윈도우 데이터를 담는 큐

        Thread {
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

            val slidingWindowBuffer = FloatArray(windowSize)  // 2초 버퍼
            var bufferPosition = 0

            audioRecord.startRecording()

            // 실시간으로 데이터를 읽어들여 모델로 전달
            while (isListening) {
                val readSize = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                if (readSize > 0) {
                    for (i in 0 until readSize) {
                        slidingWindowBuffer[bufferPosition] = audioBuffer[i] / 32768.0f
                        bufferPosition++

                        if (bufferPosition >= windowSize) {
                            val windowCopy = slidingWindowBuffer.clone()
                            audioQueue.put(windowCopy)  // 큐에 윈도우 데이터를 추가

                            // 슬라이딩 윈도우 25% 이동
                            System.arraycopy(slidingWindowBuffer, stepSize, slidingWindowBuffer, 0, windowSize - stepSize)
                            bufferPosition = windowSize - stepSize
                        }
                    }
                }
            }
            audioRecord.stop()
            audioRecord.release()
        }.start()

        // 분류 스레드
        Thread {
            try {
                val classifier = AudioClassifier(this)
                while (isListening) {
                    val slidingWindowData = audioQueue.take()  // 큐에서 데이터 가져오기

                    var isExist = false
                    for (data in slidingWindowData) {
                        if(data.toFloat() != 0f){
                            isExist = true
                            print(data)
                            print(",")
                        }
                    }

                    if (isExist){
                        println("OO Data has entered! OO")
                    } else {
                        println("XX Data has NOT entered XX")
                    }

//                    val inputBuffer = classifier.createInputBuffer(slidingWindowData)
                    val results = classifier.classify(slidingWindowData)

                    runOnUiThread {
                        mainViewModel.setResultText("확률값: ${results[0]}")
                    }

                    if (results[0] >= 0.8f) {
                        runOnUiThread {
                            showSuccessDialog()
                        }
                        audioQueue.clear()
                        isListening = false
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "분류 중 오류 발생", e)
                runOnUiThread {
                    mainViewModel.setResultText("분류 중 오류가 발생했습니다: " + e.message)
                }
            }
        }.start()
    }

    // 호출어 인식 성공 시 보여줄 팝업
    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("호출어 인식 성공")
            .setMessage("호출어가 성공적으로 인식되었습니다!")
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()  // 팝업 닫기
                realTimeRecordAndClassify()  // 스레드 재시작
            }
            .setCancelable(false)
            .show()
    }
}

