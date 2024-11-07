import os
from audiomentations import Compose, AddGaussianNoise, TimeStretch, PitchShift, Shift
import librosa
import soundfile as sf
from tqdm import tqdm

def create_augmented_audio(input_path, output_path, num_augmentations=5):
    """
    주어진 경로의 모든 wav 파일에 대해 오디오 증강을 수행하는 함수
    
    Args:
        input_path (str): 입력 오디오 파일들이 있는 경로
        output_path (str): 증강된 오디오 파일들을 저장할 경로
        num_augmentations (int): 각 파일당 생성할 증강 데이터 수
    """
    
    # 증강 파이프라인 정의
    augment = Compose([
        AddGaussianNoise(min_amplitude=0.001, max_amplitude=0.015, p=0.5),
        TimeStretch(min_rate=0.6, max_rate=1.4, p=0.5),
        PitchShift(min_semitones=-2, max_semitones=2, p=0.5),
        Shift(min_shift=-0.5, max_shift=0.5, p=0.5),
    ])
    
    # 입력 경로 생성
    input_dir = os.path.join(input_path, middle_path)
    
    # 출력 디렉토리 생성
    output_dir = os.path.join(input_path, output_path,middle_path)
    os.makedirs(output_dir, exist_ok=True)
    
    # 입력 디렉토리의 모든 wav 파일 처리
    wav_files = [f for f in os.listdir(input_dir) if f.endswith('.wav')]
    
    print(f"총 {len(wav_files)}개의 파일에 대해 각 {num_augmentations}개의 증강 데이터를 생성합니다.")
    
    for wav_file in tqdm(wav_files):
        # 원본 오디오 로드
        audio_path = os.path.join(input_dir, wav_file)
        samples, sample_rate = librosa.load(audio_path, sr=32000)
        
        # 파일명에서 확장자 제거
        filename = os.path.splitext(wav_file)[0]
        
        # 각 파일에 대해 지정된 수만큼 증강 데이터 생성
        for i in range(num_augmentations):
            # 오디오 증강 적용
            augmented_samples = augment(samples=samples, sample_rate=sample_rate)
            
            # 증강된 오디오 저장
            output_filename = f"{filename}_aug_{i+1}.wav"
            output_filepath = os.path.join(output_dir, output_filename)
            sf.write(output_filepath, augmented_samples, sample_rate)

if __name__ == "__main__":
    # 경로 설정
    audio_path = '/home/ssafy/AudioAugmentation'
    middle_path = 'hey_ssafy_32000/false_wav_32000'
    output_path = 'aug'
    
    # 증강 실행
    create_augmented_audio(audio_path, output_path, num_augmentations=5)
    print("오디오 증강이 완료되었습니다.")