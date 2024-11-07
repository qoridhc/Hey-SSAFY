package com.marusys.hesap;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.flex.FlexDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;


public class AudioClassifier {
    private static final String MODEL_FILE = "trigger_word_detection_model_MelIntoLayer_sr16000_B32_lr5e-5_pat30.tflite";
    private Interpreter tflite;
    private int inputHeight;
    private int inputWidth;
    private int inputChannels;

    public AudioClassifier(Context context) {
        try {
             Interpreter.Options options = new Interpreter.Options();
            options.addDelegate(new FlexDelegate());

            tflite = new Interpreter(loadModelFile(context, MODEL_FILE), options);

            // 입력 텐서의 형식 출력
            int[] inputShape = tflite.getInputTensor(0).shape();
            Log.d("AudioClassifier", "Model Input Shape: " + Arrays.toString(inputShape));

            // 입력 텐서의 길이에 따라 height, width, channels 설정
            inputHeight = inputShape[1];  // 16000 샘플 길이
            inputWidth = 1;  // 샘플이므로 1
            inputChannels = 1;  // 채널 값도 1로 설정
        } catch (IOException e) {
            Log.e("AudioClassifier", "Error loading model", e);
        }
    }

    public float[] classify(float[] audioData) {

        float[][] expandedAudioData = new float[1][audioData.length];
        for (int i = 0; i < audioData.length; i++) {
            expandedAudioData[0][i] = audioData[i];
        }
        float[][] outputBuffer = new float[1][1];

        // 모델 실행
        try {
            tflite.run(expandedAudioData, outputBuffer);
        } catch (Exception e) {
            e.printStackTrace(); // 오류가 발생하면 스택 트레이스를 출력합니다.
        }
        return outputBuffer[0];
    }


    // TFlite 모델 파일 로드
    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {

        // 모델 파일의 파일 디스크립터를 얻음
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);

        // 파일 디스크립터를 사용해 파일 스트림 생성
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());

        // 파일 스트림에서 파일 채널을 가져옴
        FileChannel fileChannel = inputStream.getChannel();

        // 파일의 시작 오프셋과 길이를 가져옴
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        // 파일을 읽기 전용으로 메모리에 매핑하여 반환 (모델을 메모리에 로드)
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

}