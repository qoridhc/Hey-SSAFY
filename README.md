
## 📅 기간

- 2024.10.14 ~ 2024.11.19

## ℹ 프로젝트 소개

사용자가 특정 단어(호출어)를 발화했을 때 이를 인식하고, 이후 음성 명령에 따라 반응하는 **실시간 음성 인식 및 호출어 인식 시스템**을 제공합니다. 이를 온디바이스 AI 기반 어플리케이션으로 개발하여 안드로이드 기기에서 사용할 수 있습니다.

### 주요기능
#### 1. 실시간 호출어 인식 (Keyword Spotting)
   - CNN, RNN, ResNet 세 가지 음성 인식 모델을 결합하여 실시간으로 호출어 감지
   - 다양한 모델의 조합을 통해 호출어를 높은 정확도로 인식

#### 2. 명령어 인식 및 기능 제어
   - 호출어 인식 후, TTS(텍스트 음성 변환) 기능을 통해 명령을 인식하고 해석
   - 사용자는 음성 명령으로 손전등 켜기/끄기와 같은 간단한 기능을 제어할 수 있으며, 향후 다양한 기능 추가 예정

#### 3. 안드로이드 온디바이스 AI
   - 모든 음성 인식과 명령 처리 작업을 기기 내에서 수행하도록 설계하여 온디바이스 AI 경험을 제공
   - 실시간 처리가 가능한 최적화된 구조로, 모바일 환경에서 원활하게 동작하도록 구현

<div align="center">
    <img src="/uploads/f8669126fdb1499810b9408b71a6d5f9/mockup.png" alt="mockup">
</div>

## 🛠 Getting Started

### MainActivity
```kotlin
   const val THRESHOLD = 0.95
   const val SAMPLE_RATE = 16000   // 샘플 레이트 16KHz (16000Hz)
   const val RECORDING_TIME = 2    // 녹음 시간 (2초)
   const val WINDOW_SIZE = SAMPLE_RATE * RECORDING_TIME  // 전체 window size
   const val STEP_SIZE = SAMPLE_RATE / 2     // sliding window 사이즈 (겹치는 구간)
```
변수들을 통해 다양한 녹음 관련 설정을 변경할 수 있습니다.

```kotlin
   // 모델 타입
   enum class ModelType {
       RESNET, CNN, GRU
   }

    var MODEL_TYPE: ModelType = ModelType.CNN
```
ModelType 통해 새로운 AI 모델을 쉽게 추가하고 변경할 수 있습니다.

### CNN / RNN

## 📘 Jupyter Notebook
[View Notebook on nbviewer](https://lab.ssafy.com/s11-final/S11P31S207/-/blob/develop/AI/CNN/CNN_WordTrigger_3Conv_AddDropOut_Model.ipynb)


#### AudioClassifier
```java
   private static final String MODEL_FILE = "CNN_or_RNN_Model.tflite";
```
asset 폴더에 tflite로 변환한 CNN / RNN 모델 경로를 명시해줍니다.

### Resnet
#### ResnetClassifier
```java
 private final String[] labels = {
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
        "yes",
        "hey_ssafy" // 호출어 인식을 위한 단어 레이블 추가
};
```
label에 커스텀 호출어 레이블을 추가합니다.

```java
private static final String MEL_MODEL_PATH = "mel_spectogram_convert_model.ptl";
private static final String RESNET_MODEL_PATH = "trigger_word_detection_model_with_ResNet.ptl";
```
Asset 폴더에 삽입한 AI 모델을 변환한 PyTorch Script 파일을 불러옵니다.

- **mel_spectogram_model** : 음성 데이터를 **MelSpectogram**으로 변환
- **resnet_model** : Resnet 학습 모델

