import os
from pydub import AudioSegment

def convert_m4a_to_wav(input_folder, output_folder):
    # 출력 폴더가 없으면 생성
    if not os.path.exists(output_folder):
        os.makedirs(output_folder)

    # 폴더 내의 모든 파일 확인
    for filename in os.listdir(input_folder):
        # m4a 파일만 선택
        if filename.endswith(".m4a"):
            # 입력 및 출력 파일 경로 설정
            input_file = os.path.join(input_folder, filename)
            output_file = os.path.join(output_folder, os.path.splitext(filename)[0] + ".wav")
            
            # m4a 파일을 로드하고 wav로 변환
            audio = AudioSegment.from_file(input_file, format="m4a")
            audio.export(output_file, format="wav")
            print(f"{input_file} 파일이 {output_file}로 변환되었습니다.")

# 변환할 폴더 경로 설정
input_folder = "m4a_files"  # 변환할 m4a 파일들이 있는 폴더 경로
output_folder = "wav_files"  # 저장할 wav 파일들을 저장할 폴더 경로

# 변환 실행
convert_m4a_to_wav(input_folder, output_folder)


