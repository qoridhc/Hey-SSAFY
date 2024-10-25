package com.example.audio_classification_java;

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
    private static final String MODEL_FILE = "converted_model_ramda.tflite";
    private Interpreter tflite;
    private int inputHeight;
    private int inputWidth;
    private int inputChannels;
    public AudioClassifier(Context context) {
        try {
            tflite = new Interpreter(loadModelFile(context, MODEL_FILE));

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

    // 입력 버퍼로 들어오는 값을 통해 분류를 수행하는 메소드
    public float[] classify(ByteBuffer inputBuffer) {
        // 결과를 담을 버퍼 준비 (1차원 8개의 분류 값)
        float[][] outputBuffer = new float[1][8];

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


    // ===== 1차원 원시 음성 데이터 -> 2차원 스펙토그램 데이터

//    public ByteBuffer createInputBufferBySpectogram(float[] audioData) {
//        // 스펙트로그램 생성
//        float[][] spectrogram = computeSpectrogram(audioData, inputHeight, inputWidth);
//
//        // 입력 버퍼 생성 (4 bytes per float * inputHeight * inputWidth * inputChannels)
//        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * inputHeight * inputWidth * inputChannels);
//        inputBuffer.order(ByteOrder.nativeOrder());
//
//        // 스펙트로그램 값을 버퍼에 넣기
//        for (float[] row : spectrogram) {
//            for (float value : row) {
//                inputBuffer.putFloat(value);
//            }
//        }
//
//        // 버퍼 위치를 처음으로 되돌리기 (중요!)
//        inputBuffer.rewind();
//
//        return inputBuffer;
//    }


//
//    private float[][] computeSpectrogram(float[] audioData, int targetHeight, int targetWidth) {
//        int windowSize = 256;
//        int stepSize = 128;
//        int numWindows = (audioData.length - windowSize) / stepSize + 1;
//        float[][] spectrogram = new float[numWindows][windowSize];
//
//        for (int i = 0; i < numWindows; i++) {
//            float[] window = new float[windowSize];
//            int copySize = Math.min(windowSize, audioData.length - i * stepSize); // 복사 가능한 데이터 크기 계산
//            System.arraycopy(audioData, i * stepSize, window, 0, copySize); // 복사 가능한 만큼만 복사
//            float[] magnitudes = computeFFT(window);
//            spectrogram[i] = magnitudes;
//        }
//
//        return spectrogram;
//    }
//
//    private float[] computeFFT(float[] window) {
//        double[] fftData = new double[window.length * 2];
//        for (int i = 0; i < window.length; i++) {
//            fftData[2 * i] = window[i];
//            fftData[2 * i + 1] = 0;
//        }
//        DoubleFFT_1D fft = new DoubleFFT_1D(window.length);
//        fft.complexForward(fftData);
//
//        float[] magnitudes = new float[window.length / 2];
//        for (int i = 0; i < magnitudes.length; i++) {
//            double real = fftData[2 * i];
//            double imaginary = fftData[2 * i + 1];
//            magnitudes[i] = (float) Math.sqrt(real * real + imaginary * imaginary);
//        }
//        return magnitudes;
//    }

}