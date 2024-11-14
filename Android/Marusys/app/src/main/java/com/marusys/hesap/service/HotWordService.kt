package com.marusys.hesap.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.AudioClassifier
import com.marusys.hesap.R
import com.marusys.hesap.feature.VoiceRecognitionState
import com.marusys.hesap.feature.VoiceStateManager
import com.marusys.hesap.presentation.viewmodel.MainViewModel
import com.marusys.hesap.service.AudioConstants.RECORDING_TIME
import com.marusys.hesap.service.AudioConstants.SAMPLE_RATE
import com.marusys.hesap.service.AudioConstants.STEP_SIZE
import com.marusys.hesap.service.AudioConstants.THRESHOLD
import com.marusys.hesap.service.AudioConstants.WINDOW_SIZE

object AudioConstants {
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
private val TAG = "HotWordService"
class HotWordService : Service() {
//    private lateinit var audioServiceIntent: Intent
    private lateinit var wakeLock: PowerManager.WakeLock
    private val mainViewModel: MainViewModel by lazy {
        MainViewModel()
    }
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "BackgroundServiceChannel"
    // 모델 타입
    enum class ModelType {
        RESNET, CNN, GRU
    }

    var MODEL_TYPE: ModelType = ModelType.GRU

    // 사용자에게 모델 타입을 선택할 수 있게 해주는 메서드
    private fun startRecordingWithModel() {
//        when (MODEL_TYPE) {
//            ModelType.RESNET -> resnetRealTimeRecordAndClassify()
//            ModelType.CNN -> cnnRealTimeRecordAndClassify()
//            ModelType.GRU -> gruRealTimeRecordAndClassify()
    }
    override fun onCreate() {
        super.onCreate()
        Log.e(TAG,"호출어 탐지 시작")
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HotWordService::WakeLock")
        gruRealTimeRecordAndClassify()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG,"onstartCommand")
        startForeground(NOTIFICATION_ID, createNotification())
        wakeLock.acquire()
        gruRealTimeRecordAndClassify()
        return START_STICKY
    }

    override fun onDestroy() {
        Log.e(TAG,"ondestroy")
        super.onDestroy()
    }
    override fun onBind(intent: Intent): IBinder?  = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("앱 실행 중")
            .setContentText("앱이 백그라운드에서 실행 중입니다.")
            .setSmallIcon(R.drawable.marusys_icon)
            .build()
    }

    // 로출어 인식 -> 서비스 시작
    private fun startAudioService() {
//        val bundle = Bundle()
//        bundle.putString("commandText", mainViewModel.commandText.value)
//        bundle.putBoolean("isAudioServiceRunning", true)
        // intent AudioService로 넘기기
//        val serviceIntent = Intent(this, AudioService::class.java)
//        serviceIntent.putExtra("viewModelState", bundle)
//        audioServiceIntent = Intent(this, AudioService::class.java)
//        audioServiceIntent.putExtra("viewModelState", bundle)
        // 포그라운드 Service 시작
//        ContextCompat.startForegroundService(this, serviceIntent)
        val intent = Intent(this, AudioService::class.java)
        VoiceStateManager.updateState(VoiceRecognitionState.HotwordDetecting) // 호출어 인식 완료, isListen = false
        startForegroundService(intent)
    }
    private fun sendResultUpdate(result: String) {
        val intent = Intent("RESULT_UPDATE")
        intent.putExtra("result", result)
        Log.e(TAG,"결과 업데이트해요 $result")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    fun gruRealTimeRecordAndClassify() {
        // 오디오 녹음을 위한 버퍼 크기 계산
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
        // UI 업데이트
        sendResultUpdate("듣고 있는 중이에요.")
        Log.e(TAG,"호출어 듣는 중~")
        //
        Thread {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG,"녹음 초기화 안됨")
                sendResultUpdate("녹음 초기화 실패")
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
                                val percentage = String.format("%.2f%%", results[0] * 100)
                                Log.e(TAG,"확률값 내는 중 $percentage")
                                sendResultUpdate("확률값: $percentage")

                                // 호출어가 감지되면 팝업을 띄우고 스레드를 중단
                                if (results[0] >= THRESHOLD) {
                                        startAudioService() // AudioService 시작
                                    break  // 루프 종료
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "분류 중 오류 발생", e)
                                sendResultUpdate("분류 중 오류가 발생했습니다: " + e.message)
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
            audioRecord.stop() //녹음 종료
            audioRecord.release() // 리소스 해제
        }.start()
    }

}
