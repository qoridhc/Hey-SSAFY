import os
from audiomentations import Compose, AddGaussianNoise, TimeStretch, PitchShift
import librosa
import soundfile as sf
from tqdm import tqdm

def create_augmented_audio(input_path, output_path, num_augmentations=5, sample_length=32000, sample_rate=16000):
    """
    주어진 경로의 모든 wav 파일에 대해 오디오 증강을 수행하고 길이를 2초로 고정하는 함수
    
    Args:
        input_path (str): 입력 오디오 파일들이 있는 경로
        output_path (str): 증강된 오디오 파일들을 저장할 경로
        num_augmentations (int): 각 파일당 생성할 증강 데이터 수
        sample_length (int): 모든 오디오 파일의 최종 샘플 길이 (기본값: 32000 샘플 = 2초)
        sample_rate (int): 샘플링 레이트 (기본값: 16000Hz)
    """
    
    # 증강 파이프라인 정의 (Shift 제거)
    augment = Compose([
        AddGaussianNoise(min_amplitude=0.001, max_amplitude=0.015, p=0.5),
        TimeStretch(min_rate=0.6, max_rate=1.4, p=0.5),
        PitchShift(min_semitones=-2, max_semitones=2, p=0.5)
    ])
    
    # 입력 및 출력 경로 설정
    os.makedirs(output_path, exist_ok=True)
    wav_files = [f for f in os.listdir(input_path) if f.endswith('.wav')]
    
    print(f"총 {len(wav_files)}개의 파일에 대해 각 {num_augmentations}개의 증강 데이터를 생성합니다.")
    
    for wav_file in tqdm(wav_files):
        # 원본 오디오 로드
        audio_path = os.path.join(input_path, wav_file)
        samples, _ = librosa.load(audio_path, sr=sample_rate)
        
        # 파일명에서 확장자 제거
        filename = os.path.splitext(wav_file)[0]
        
        # 각 파일에 대해 지정된 수만큼 증강 데이터 생성
        for i in range(num_augmentations):
            # 오디오 증강 적용
            augmented_samples = augment(samples=samples, sample_rate=sample_rate)
            
            # 증강 후 길이 고정 (2초, 16000Hz 샘플링 기준 32000 샘플)
            if len(augmented_samples) < sample_length:
                augmented_samples = librosa.util.fix_length(augmented_samples, size=sample_length)
            else:
                augmented_samples = augmented_samples[:sample_length]
            
            # 증강된 오디오 저장
            output_filename = f"{filename}_aug_{i+1}.wav"
            output_filepath = os.path.join(output_path, output_filename)
            sf.write(output_filepath, augmented_samples, sample_rate)

# 경로 설정 및 실행 예시
if __name__ == "__main__":
    input_path = r'C:\Users\SSAFY\Desktop\tf\tempToWav'  # 원본 오디오 파일 경로
    output_path = r'C:\Users\SSAFY\Desktop\tf\augmented_wav'  # 증강된 오디오 파일 저장 경로
    
    # 증강 실행
    create_augmented_audio(input_path, output_path, num_augmentations=5, sample_length=32000, sample_rate=16000)
    print("오디오 증강이 완료되었습니다.")