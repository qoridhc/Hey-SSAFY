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
import org.jtransforms.fft.FloatFFT_1D;


public class AudioClassifier {
    private static final String MODEL_FILE = "trigger_word_detection_model_B32_lr5e-5_pat30.tflite";
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
            //inputHeight = inputShape[1];  // 16000 샘플 길이
            //inputWidth = 1;  // 샘플이므로 1
            inputHeight = 128;
            inputWidth = 126;
            inputChannels = 1;  // 채널 값도 1로 설정
        } catch (IOException e) {
            Log.e("AudioClassifier", "Error loading model", e);
        }
    }

    public float[] classify(float[][] spectrogram) {
        // 스펙트로그램의 차원을 (1, time_steps, n_mels) 형태로 맞추기
        float[][][] expandedSpectrogram = new float[1][spectrogram.length][spectrogram[0].length];
        for (int i = 0; i < spectrogram.length; i++) {
            for (int j = 0; j < spectrogram[0].length; j++) {
                expandedSpectrogram[0][i][j] = spectrogram[i][j];
            }
        }
        System.out.println(expandedSpectrogram.length);
        System.out.println(expandedSpectrogram[0].length);
        System.out.println(expandedSpectrogram[0][0].length);
        float[][] outputBuffer = new float[1][1];


// 모델 실행
        try {
            tflite.run(expandedSpectrogram, outputBuffer);
        } catch (Exception e) {
            e.printStackTrace(); // 오류가 발생하면 스택 트레이스를 출력합니다.
        }
        return outputBuffer[0];
    }


    // 오디오 데이터를 ByteBuffer로 변환하는 메서드
    public float[][] createInputBuffer(float[] audioData) {
        // Mel-spectrogram 생성

        int nMels = 128;
        int inputWidth = 126; // 예시로 가정한 가로 길이 (time_steps)
        float[][] spectrogram = new float[nMels][inputWidth];
        float[][] ret = new float[inputWidth][nMels];

        // FFT로 간단히 변환하고 log-scale을 적용하는 코드
        FloatFFT_1D fft = new FloatFFT_1D(audioData.length);
        fft.realForward(audioData);

        for (int i = 0; i < nMels; i++) {
            for (int j = 0; j < inputWidth; j++) {
                spectrogram[i][j] = (float) Math.log10(1 + Math.abs(audioData[j % audioData.length]));  // 단순화된 로그 변환
            }
        }

        for (int i = 0; i < inputWidth; i++) {
            for (int j = 0; j < nMels; j++) {
                ret[i][j] = spectrogram[j][i]; // 단순화된 로그 변환
            }
        }
        return ret;
    }
//
//    public ByteBuffer createInputBuffer(float[] audioData) {
//        // 입력 버퍼 생성 (4 bytes per float * 16000 samples * 1 channel)
//        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * inputHeight * inputWidth * inputChannels);
//        inputBuffer.order(ByteOrder.nativeOrder());
//
//        // MFCC 설정
//        int sampleRate = 16000;
//        MFCC mfcc = new MFCC(13, sampleRate); // 13차원 MFCC
//        AudioDispatcher dispatcher = AudioDispatcherFactory.fromFloatArray(audioData, sampleRate);
//        dispatcher.addAudioProcessor(mfcc);
//
//        // MFCC 프로세서 설정
//        float[] mfccValues = new float[13]; // 13차원 MFCC 결과를 저장할 배열
//        dispatcher.addAudioProcessor(new MFCCProcessor() {
//            @Override
//            public void process(MFCC mfcc) {
//                System.arraycopy(mfcc.getMFCC(), 0, mfccValues, 0, mfccValues.length);
//            }
//        });
//
//        // 오디오 데이터를 MFCC로 변환
//        dispatcher.run(); // dispatcher 실행하여 MFCC 계산
//
//        // MFCC 결과를 버퍼에 넣기
//        for (float value : mfccValues) {
//            inputBuffer.putFloat(value);
//        }
//
//        // 버퍼 위치를 처음으로 되돌리기
//        inputBuffer.rewind();
//
//        return inputBuffer;
//    }

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