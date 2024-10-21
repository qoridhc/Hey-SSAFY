package com.example.audio_classification_java;

import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    // 결과를 출력할 텍스트 뷰
    private TextView resultTextView;

    // 분류할 라벨들 -> 모델 학습 시 사용한 라벨
    private String[] labels = {
            "down",
            "go",
            "left",
            "no",
            "right",
            "stop",
            "up",
            "yes"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 텍스트뷰와 버튼을 UI와 연결
        resultTextView = findViewById(R.id.resultTextView);

        Button recordButton = findViewById(R.id.recordButton);

        // 버튼 클릭 리스너 설정 -> 버튼 클릭 시 작동
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 오디오 녹음 권한이 있는지 확인
                if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    // 권한이 없으면 요청 메서드 실행
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 1);
                } else {
                    // 권한 설정 체크 후 음성 녹음 및 분류 코드 실행
                    recordAndClassify();
                }
            }
        });
    }


    // 녹음 권한을 요청하는 메서드
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 요청한 권한의 응답 결과 처리
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한이 부여되면 녹음 및 분류 시작
                Log.d("MainActivity", "녹음 권한이 부여되었습니다.");
                recordAndClassify();
            } else {
                Log.e("MainActivity", "녹음 권한이 거부되었습니다.");
            }
        }
    }

    // ======== 음성 인식 기반 분류 ========
    // 현재는 버튼 리스너 기반 -> 추후에 실시간 음성인식 코드 구현

    public void recordAndClassify() {

        // 샘플 레이트 16KHz(16000Hz)
        int sampleRate = 16000;

        // 녹음 설정 시간 ( 1초로 설정 )
        int recordingTime = 1;

        // 샘플 갯수 계산
        int totalSamples = sampleRate * recordingTime;

        // 최소 버퍼 크기를 얻어옴
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        // 녹음 권한이 있는지 재확인
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // 녹음 상태 표시해주기 (스레드 별도 설정)
        runOnUiThread(() -> {
            resultTextView.setText("녹음 중...");
        });

        // 백그라운드 스레드에서 녹음 및 분류 실행
        new Thread(() -> {
            AudioRecord audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, // 마이크에서 오디오 소스 가져옴
                    sampleRate,                    // 샘플레이트 설정 (16kHz)
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize                     // 버퍼 크기 설정
            );

            // AudioRecord 초기화 실패 시 로그 출력 및 종료
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e("MainActivity", "AudioRecord 초기화 실패");
                runOnUiThread(() -> {
                    resultTextView.setText("녹음 초기화 실패");
                });
                return;
            }

            // 녹음할 샘플을 저장할 버퍼 생성
            short[] audioBuffer = new short[totalSamples];

            // 녹음 시작
            audioRecord.startRecording();

            // 녹음 데이터 읽기
            audioRecord.read(audioBuffer, 0, audioBuffer.length);

            // 녹음 종료 & 리소스 해제
            audioRecord.stop();
            audioRecord.release();

            Log.d("원시 데이터", Arrays.toString(audioBuffer));

            // short 배열을 float 배열로 변환 (정규화 포함)
            float[] audioData = new float[16000];
            for (int i = 0; i < audioData.length; i++) {
                // 16비트 데이터 정규화 (-1.0 ~ 1.0 값으로 맞춰줌)
                audioData[i] = audioBuffer[i] / 32768.0f;
            }

            // 입력 음성 데이터 값 로그
            Log.d("audioData", Arrays.toString(audioData));

            try {
                // 자체 AudioClassifier를 사용하여 분류
                AudioClassifier classifier = new AudioClassifier(this);

                // 입력 데이터를 분류 모델의 형식에 맞게 변환
                ByteBuffer inputBuffer = classifier.createInputBuffer(audioData);
                float[] results = classifier.classify(inputBuffer);

                // 소프트맥스 적용
                float max = Float.NEGATIVE_INFINITY;
                for (float result : results) {
                    if (result > max) max = result;
                }

                float sum = 0;
                float[] softmaxResults = new float[results.length];

                for (int i = 0; i < results.length; i++) {
                    softmaxResults[i] = (float) Math.exp(results[i] - max);
                    sum += softmaxResults[i];
                }

                // 소프트맥스 결과 정규화
                for (int i = 0; i < softmaxResults.length; i++) {
                    softmaxResults[i] /= sum;
                }

                // 결과 문자열 생성 (결과 값 포맷팅)
                StringBuilder resultText = new StringBuilder();
                for (int i = 0; i < results.length; i++) {
                    resultText.append(labels[i])
                            .append(" : ")
                            .append(String.format("%.4f", results[i]))
                            .append("(")
                            .append(String.format("%.2f", softmaxResults[i] * 100))
                            .append("%)\n");
                }

                final String finalResult = resultText.toString();

                // 결과 출력
                runOnUiThread(() -> {
                    resultTextView.setText(finalResult);
                });

                Log.d("resultText", finalResult);

            } catch (Exception e) {
                Log.e("MainActivity", "분류 중 오류 발생", e);
                runOnUiThread(() -> {
                    resultTextView.setText("분류 중 오류가 발생했습니다: " + e.getMessage());
                });
            }
        }).start();
    }

    // ======== wav 파일 기반 분류 ========

//    public void classifyWavFile(String fileName) throws IOException {
//        float[] audioData = readWavFile(fileName);
//
//        // 데이터 길이가 16000인지 확인하고 필요시 조정
//        if (audioData.length != 16000) {
//            float[] resizedAudio = new float[16000];
//            if (audioData.length > 16000) {
//                System.arraycopy(audioData, 0, resizedAudio, 0, 16000);
//            } else {
//                System.arraycopy(audioData, 0, resizedAudio, 0, audioData.length);
//            }
//            audioData = resizedAudio;
//        }
//
//        AudioClassifier classifier = new AudioClassifier(this);
//        ByteBuffer inputBuffer = classifier.createInputBuffer(audioData);
//        float[] results = classifier.classify(inputBuffer);
//
//        // 소프트맥스 적용
//        float max = Float.NEGATIVE_INFINITY;
//        for (float result : results) {
//            if (result > max) max = result;
//        }
//
//        float sum = 0;
//        float[] softmaxResults = new float[results.length];
//        for (int i = 0; i < results.length; i++) {
//            softmaxResults[i] = (float) Math.exp(results[i] - max);
//            sum += softmaxResults[i];
//        }
//
//        for (int i = 0; i < softmaxResults.length; i++) {
//            softmaxResults[i] /= sum;
//        }
//
//        // 결과 출력
//        StringBuilder resultText = new StringBuilder();
//        for (int i = 0; i < results.length; i++) {
//            resultText.append(labels[i])  // 여기는 String.format 필요 없음
//                    .append(" : ")
//                    .append(String.format("%.4f", results[i]))  // %로 포맷
//                    .append("(")
//                    .append(String.format("%.2f", softmaxResults[i] * 100))  // %로 포맷
//                    .append("%)\n");
//        }
//
//        resultTextView.setText(resultText.toString());
//        Log.d("resultText", resultText.toString());
//    }
//
//    public float[] readWavFile(String fileName) throws IOException {
//        InputStream inputStream = getAssets().open(fileName);
//        byte[] data = new byte[inputStream.available()];
//        inputStream.read(data);
//        inputStream.close();
//
//        // Skip WAV header (44 bytes)
//        int headerSize = 44;
//        int audioDataSize = (data.length - headerSize) / 2; // 16-bit audio = 2 bytes per sample
//        float[] audioData = new float[audioDataSize];
//
//        // Convert bytes to float and normalize
//        for (int i = 0; i < audioDataSize; i++) {
//            // Convert 2 bytes to 16-bit integer
//            short sample = (short) ((data[headerSize + 2*i + 1] << 8) | (data[headerSize + 2*i] & 0xFF));
//            // Normalize to -1.0 to 1.0
//            audioData[i] = sample / 32768.0f;  // 32768 = 2^15 (maximum value for 16-bit audio)
//        }
//
//        return audioData;
//    }


}



