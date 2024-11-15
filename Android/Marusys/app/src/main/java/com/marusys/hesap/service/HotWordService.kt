package com.marusys.hesap.service

import android.Manifest
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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.marusys.hesap.AudioClassifier
import com.marusys.hesap.R
import com.marusys.hesap.ResnetClassifier
import com.marusys.hesap.feature.VoiceRecognitionState
import com.marusys.hesap.feature.VoiceStateManager
import com.marusys.hesap.presentation.components.AudioNotification
import com.marusys.hesap.presentation.components.AudioNotification.Companion.NOTIFICATION_ID
import com.marusys.hesap.service.AudioConstants.RECORDING_TIME
import com.marusys.hesap.service.AudioConstants.SAMPLE_RATE
import com.marusys.hesap.service.AudioConstants.STEP_SIZE
import com.marusys.hesap.service.AudioConstants.THRESHOLD
import com.marusys.hesap.service.AudioConstants.TRIGGER_WORD
import com.marusys.hesap.service.AudioConstants.WINDOW_SIZE
import com.marusys.hesap.util.ThresholdUtil

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
object ResultsLiveData {
    private val _results = MutableLiveData<FloatArray>()
    val results: LiveData<FloatArray> = _results

    fun updateResults(newResults: FloatArray) {
        _results.postValue(newResults)
    }
}
private val TAG = "HotWordService"

class HotWordService() : Service() {
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var audioNotification: AudioNotification
    private lateinit var classifier: AudioClassifier
    private lateinit var audioIntent: Intent

    // 모델 타입
    enum class ModelType {
        RESNET, CNN, GRU
    }

    private var MODEL_TYPE: ModelType = ModelType.CNN

    // 사용자에게 모델 타입을 선택할 수 있게 해주는 메서드
    private fun startRecordingWithModel() {
        when (MODEL_TYPE) {
            ModelType.RESNET -> resnetRealTimeRecordAndClassify()
            ModelType.CNN -> cnnRealTimeRecordAndClassify()
            ModelType.GRU -> gruRealTimeRecordAndClassify()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "HotWord 서비스 시작 : 호출어 탐지 시작")
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HotWordService::WakeLock")
        wakeLock.acquire(10*60*1000L /*10 minutes*/)
        audioNotification = AudioNotification(this, ResultsLiveData.results)
        startForeground(NOTIFICATION_ID, audioNotification.getNotification()) //알림창 실행
        classifier = AudioClassifier(this)
        startRecordingWithModel() // 호출어 인식
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "onstartCommand")
        startRecordingWithModel()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "hot word onDestroy")
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        ResultsLiveData.results.removeObserver { }
        stopForeground(STOP_FOREGROUND_DETACH)
        // 알림 유지
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, audioNotification.getNotification())
        // 서비스 재시작
        if (VoiceStateManager.voiceState.value == VoiceRecognitionState.WaitingForHotword){
        val restartServiceIntent = Intent(applicationContext, HotWordService::class.java)
//        val restartServiceIntent = Intent(this, HotWordService::class.java)
        startForegroundService(restartServiceIntent)
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun sendResultUpdate(result: String) {
        val intent = Intent("RESULT_UPDATE")
        intent.putExtra("result", result)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    // 로출어 인식 -> 서비스 시작
    private fun startAudioService() {
        Log.d(TAG, "startAudioSErvice 시작")
        val bundle = Bundle()
        bundle.putBoolean("isAudioServiceRunning", true)
        // intent AudioService로 넘기기
        audioIntent = Intent(this, AudioService::class.java)
        audioIntent.putExtra("viewModelState", bundle)
        // 포그라운드 Service 시작
        startForegroundService(audioIntent)
//        startService(audioIntent)
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
        // UI 업데이트
        sendResultUpdate("듣고 있는 중이에요.")
        Log.e(TAG, "호출어 듣는 중~")

        Thread {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 초기화 실패")
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
                                val inputBuffer = classifier.createInputBuffer(slidingWindowBuffer)
                                val results = classifier.classify(inputBuffer)
                                // LiveData 업데이트
                                ResultsLiveData.updateResults(results)
                                // results[0] 값을 실시간으로 화면에 표시
                                val percentage = String.format("%.2f%%", results[0] * 100)
                                Log.e(TAG, "확률값 내는 중 $percentage")
                                sendResultUpdate("확률값: $percentage")
                                // 호출어가 감지되면 팝업을 띄우고 스레드를 중단
                                if (results[0] >= THRESHOLD) {
                                    stopForeground(STOP_FOREGROUND_DETACH)
                                    stopSelf()
                                    VoiceStateManager.updateState(VoiceRecognitionState.HotwordDetecting)
                                    startAudioService()
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
            audioRecord.stop()
            audioRecord.release()
        }.start()
    }
    // Resnet
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
        // UI 업데이트
        sendResultUpdate("듣고 있는 중이에요.")
        Log.e(TAG, "호출어 듣는 중~")
        Thread {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "녹음 초기화 안됨")
                sendResultUpdate("녹음 초기화 실패")
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
                                ResultsLiveData.updateResults(results) // f라이브 데이터 갱신
                                val accuracy = ThresholdUtil.checkTrigger(results)
                                val resultLabel = classifier.getLabel(results)

                                val resultText = StringBuilder()
                                val percentage = String.format("%.2f%%", accuracy * 100)

                                resultText.append(classifier.getLabel(results))
                                resultText.append(" : ")
                                resultText.append(percentage)

                                val finalResult = resultText.toString()
                                // 정확도 값을 실시간으로 화면에 표시
                                sendResultUpdate(finalResult)

                                // 호출어가 감지되면 팝업을 띄우고 스레드를 중단
                                if (accuracy >= THRESHOLD && resultLabel.equals(TRIGGER_WORD)) {
                                    stopForeground(STOP_FOREGROUND_DETACH)
                                    stopSelf()
                                    VoiceStateManager.updateState(VoiceRecognitionState.HotwordDetecting)
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
            audioRecord.stop()
            audioRecord.release()
        }.start()
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
        Log.e(TAG, "호출어 듣는 중~")
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
                Log.e(TAG, "녹음 초기화 안됨")
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

                                ResultsLiveData.updateResults(results)
                                // results[0] 값을 실시간으로 화면에 표시
                                val percentage = String.format("%.2f%%", results[0] * 100)
// LiveData 업데이트
                                sendResultUpdate("확률값: $percentage")
                                Log.d(TAG,"확률값 : $percentage ${audioRecord.state}")

                                // 호출어가 감지되면 팝업을 띄우고 스레드를 중단
                                if (results[0] >= THRESHOLD) {
                                    stopForeground(STOP_FOREGROUND_DETACH) //현재 서비스의 포그라운드 상태를 해제
                                    stopSelf() // 현재 서비스 종료
                                    VoiceStateManager.updateState(VoiceRecognitionState.HotwordDetecting)
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
