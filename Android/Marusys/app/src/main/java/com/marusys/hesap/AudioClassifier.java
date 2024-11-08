package com.marusys.hesap;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AudioClassifier {
    private static final String MODEL_FILE = "hey_ssafy_v2.tflite";
    private Interpreter tflite;
    private int inputHeight;
    private int inputWidth;
    private int inputChannels;
    public AudioClassifier(Context context) {
        try {
            tflite = new Interpreter(loadModelFile(context, MODEL_FILE));

            // 입력 텐서의 형식 출력
            int[] inputShape = tflite.getInputTensor(0).shape();
//            Log.d("AudioClassifier", "Model Input Shape: " + Arrays.toString(inputShape));

            // 입력 텐서의 길이에 따라 height, width, channels 설정
            inputHeight = inputShape[1];  // 16000 샘플 길이
            inputWidth = 1;  // 샘플이므로 1
            inputChannels = 1;  // 채널 값도 1로 설정

        } catch (IOException e) {
            Log.e("AudioClassifier", "Error loading model", e);
        }
    }

    // 입력 버퍼로 들어오는 값을 통해 분류를 수행하는 메소드
    public float[] classify(ByteBuffer inputBuffer) {
        // 결과를 담을 버퍼 준비 (1차원 8개의 분류 값)
        float[][] outputBuffer = new float[1][1];

        // 모델 실행 전 입력 shape 확인
        Object[] inputs = new Object[]{inputBuffer};
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, outputBuffer);

        // 모델 실행
        tflite.runForMultipleInputsOutputs(inputs, outputs);

        float[] result = outputBuffer[0];

        return result;
    }

    // 오디오 데이터를 ByteBuffer로 변환하는 메소드
    public ByteBuffer createInputBuffer(float[] audioData) {

        // 입력 버퍼 생성 (4 bytes per float * 16000 samples * 1 channel)
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * inputHeight * inputWidth * inputChannels);

        inputBuffer.order(ByteOrder.nativeOrder());

        // 오디오 데이터를 버퍼에 넣기
        for (float value : audioData) {
            inputBuffer.putFloat(value);
        }

        // 버퍼 위치를 처음으로 되돌리기
        inputBuffer.rewind();

        return inputBuffer;
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