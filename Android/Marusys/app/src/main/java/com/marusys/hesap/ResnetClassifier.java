package com.marusys.hesap;

import android.content.Context;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ResnetClassifier {

    private Module model, melModel;

    private String MelPath = "logmel2.ptl";
    private String path = "model2.ptl";

    private int inputHeight = 40;
    private int inputWidth = 101;
    private int inputChannels;
    private Context context; // context를 필드로 선언

    public ResnetClassifier(Context context) throws IOException {
        // 모델 파일을 로드합니다.
        this.context = context;

        model = LiteModuleLoader.load(assetFilePath(context, path));
        melModel = LiteModuleLoader.load(assetFilePath(context, MelPath));
    }

    // 모델의 추론 메서드
    public float[] predict(ByteBuffer inputData) {

        // 입력 텐서 생성
        Tensor inputTensor = Tensor.fromBlob(inputData, new long[]{1, 1, inputHeight, inputWidth});

        // 추론 실행
        Tensor outputTensor = model.forward(IValue.from(inputTensor)).toTensor();

        // 추론 결과 반환
        return outputTensor.getDataAsFloatArray();
    }

    public String classifyAudio(float[] audioData) {
        try {

            // 1. 오디오 데이터를 입력 텐서로 변환
            Tensor audioTensor = Tensor.fromBlob(audioData, new long[]{1, 16000});

            // 2. Mel spectrogram 계산

            Tensor melSpecTensor = melModel.forward(IValue.from(audioTensor)).toTensor();

//            long[] shape = melSpecTensor.shape();
//            Log.d("MelSpecTensor Shape", Arrays.toString(shape));

            // 3. 데이터를 추출한 후 필요한 모양으로 새로운 텐서를 생성
            float[] melSpecData = melSpecTensor.getDataAsFloatArray();
//            Log.e("convertToMel", Arrays.toString(melSpecData));

            Tensor reshapedMelTensor = Tensor.fromBlob(melSpecData, new long[]{1L, 1L, 40L, 101L});

            // 4. BCResNet으로 분류
            Tensor outputTensor = model.forward(IValue.from(reshapedMelTensor)).toTensor();

            // 5. 결과 변환
            float[] result = outputTensor.getDataAsFloatArray();
//            Log.e("classiFyResult",Arrays.toString(result));

            // 7. 레이블 반환
            return getLabel(result);

        } catch (Exception e) {
            Log.e("AudioProcessor", "Error classifying audio", e);
            return "_unknown_";
        }
    }


    private String getLabel(float[] result) {
        String[] labels = {
                "_silence_",
                "_unknown_",
                "down",
                "go",
                "left",
                "no",
                "off",
                "on",
                "right",
                "stop",
                "up",
                "yes"
        };

        // 결과 배열에서 최대값의 인덱스를 찾습니다
        int maxIndex = 0;
        for (int i = 1; i < result.length; i++) {
            if (result[i] > result[maxIndex]) {
                maxIndex = i;
            }
        }

        // 최대값 인덱스를 사용하여 레이블을 반환합니다
        return labels[maxIndex];
    }


    // assets 폴더에서 모델 파일을 복사하여 경로를 반환
    private String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (!file.exists()) {
            try (InputStream is = context.getAssets().open(assetName);
                 FileOutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
        }
        return file.getAbsolutePath();
    }

    // 오디오 데이터를 ByteBuffer로 변환하는 메서드
    public ByteBuffer createInputBuffer(float[] audioData) {

        // 입력 버퍼 생성 (4 bytes per float * 16000 samples * 1 channel)
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * 16000);

        inputBuffer.order(ByteOrder.nativeOrder());

        // 오디오 데이터를 버퍼에 넣기
        for (float value : audioData) {
            inputBuffer.putFloat(value);
        }

        // 버퍼 위치를 처음으로 되돌리기
        inputBuffer.rewind();

        return inputBuffer;
    }


    // ======== wav 파일 기반 분류 ========

    public float[] byteBufferToFloatArray(ByteBuffer buffer) {
        buffer.rewind(); // 버퍼 위치를 처음으로 이동
        int floatCount = buffer.remaining() / Float.BYTES;
        float[] floatArray = new float[floatCount];
        buffer.asFloatBuffer().get(floatArray); // FloatBuffer로 변환 후 float[]로 가져오기
        return floatArray;
    }

    public ByteBuffer readWavFile(String fileName) throws IOException {
        InputStream inputStream = context.getAssets().open(fileName);
        byte[] data = new byte[inputStream.available()];
        inputStream.read(data);
        inputStream.close();

        // Skip WAV header (44 bytes)
        int headerSize = 44;
        int audioDataSize = (data.length - headerSize) / 2; // 16-bit audio = 2 bytes per sample
        float[] audioData = new float[audioDataSize];

        // Convert bytes to float and normalize
        for (int i = 0; i < audioDataSize; i++) {
            // Convert 2 bytes to 16-bit integer
            short sample = (short) ((data[headerSize + 2*i + 1] << 8) | (data[headerSize + 2*i] & 0xFF));
            // Normalize to -1.0 to 1.0
            audioData[i] = sample / 32768.0f;  // 32768 = 2^15 (maximum value for 16-bit audio)
        }

        AudioClassifier classifier = new AudioClassifier(context);

        // 입력 데이터를 분류 모델의 형식에 맞게 변환
        ByteBuffer inputBuffer = classifier.createInputBuffer(audioData);

        return inputBuffer;
    }

    public String classifyFromFile(String assetFilePath) {
        try {
            // 파일 데이터를 ByteBuffer로 변환
            ByteBuffer audioData = readWavFile(assetFilePath);

            float[] audioBuffer = byteBufferToFloatArray(audioData);

            // float[]를 이용하여 분류
            String results = classifyAudio(audioBuffer);

            return results;

            // 결과 레이블 반환
        } catch (IOException e) {

            Log.e("ResnetClassifier", "Error reading file for classification", e);
            return "_unknown_";
        }
    }


}
