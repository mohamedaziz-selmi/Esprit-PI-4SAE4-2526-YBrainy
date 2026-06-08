#!/usr/bin/env python3
import os, subprocess, sys, urllib.request
from pathlib import Path

CONDA_ENV = "ybrainy-gaze"
PYTHON_VERSION = "3.10"

def get_conda():
    for cmd in ("conda", "conda.bat", "conda.exe"):
        try:
            result = subprocess.run([cmd, "--version"], capture_output=True, text=True)
            if result.returncode == 0:
                return cmd
        except FileNotFoundError:
            continue
    return None

def conda_env_exists(conda, env_name):
    result = subprocess.run([conda, "env", "list"], capture_output=True, text=True)
    return env_name in result.stdout

def get_conda_python(env_name):
    base = os.environ.get("CONDA_PREFIX_1") or os.environ.get("CONDA_ROOT")
    if not base:
        result = subprocess.run(["conda", "info", "--base"], capture_output=True, text=True)
        base = result.stdout.strip()
    python = Path(base) / "envs" / env_name / "python.exe"
    if python.exists():
        return str(python)
    python = Path(base) / "envs" / env_name / "bin" / "python"
    if python.exists():
        return str(python)
    return None

def install_deps(python_exe):
    print(f'Installing dependencies using {python_exe}...')
    base_flag = ['--disable-pip-version-check']

    # Read requirements, replacing any bare 'numpy' with 'numpy<2' so pip's
    # resolver sees the constraint globally (mediapipe ABI requires NumPy 1.x).
    reqs = []
    for line in open('requirements.txt', encoding='utf-8-sig'):
        r = line.strip()
        if not r:
            continue
        if r.lower() == 'numpy':
            reqs.append('numpy<2')
        else:
            reqs.append(r)
    if not any(r.lower().startswith('numpy') for r in reqs):
        reqs.insert(0, 'numpy<2')

    print('Installing all dependencies (numpy<2 pinned for mediapipe ABI)...')
    subprocess.check_call([python_exe, '-m', 'pip', 'install'] + base_flag + reqs)

def download_model():
    Path('models').mkdir(exist_ok=True)
    url = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task'
    path = Path('models/face_landmarker_v2.task')
    if not path.exists():
        print('Downloading face_landmarker_v2.task...')
        try:
            urllib.request.urlretrieve(url, path)
            print('Downloaded face_landmarker_v2.task')
        except Exception as e:
            print(f'WARNING: Could not download model: {e}')
            print(f'  Download manually if needed: {url}')
    else:
        print('face_landmarker_v2.task already exists')

if __name__ == '__main__':
    major, minor = sys.version_info[:2]

    if major == 3 and minor >= 12:
        print(f'Python {major}.{minor} detected — mediapipe requires NumPy <2 (not available on 3.12+ without issues).')
        conda = get_conda()
        if not conda:
            print('\nERROR: conda not found. Please install Anaconda or create a Python 3.10 venv manually.')
            sys.exit(1)

        if not conda_env_exists(conda, CONDA_ENV):
            print(f'\nCreating conda environment "{CONDA_ENV}" with Python {PYTHON_VERSION}...')
            subprocess.check_call([conda, "create", "-n", CONDA_ENV, f"python={PYTHON_VERSION}", "-y"])
            print(f'Environment "{CONDA_ENV}" created.')
        else:
            print(f'\nConda environment "{CONDA_ENV}" already exists.')

        python_exe = get_conda_python(CONDA_ENV)
        if not python_exe:
            print(f'\nERROR: Could not locate python.exe in "{CONDA_ENV}" environment.')
            print(f'Please run manually:\n  conda activate {CONDA_ENV}\n  python setup.py')
            sys.exit(1)

        install_deps(python_exe)
        download_model()
        print(f'\nSetup complete.')
        print(f'To run:  conda activate {CONDA_ENV}')
        print(f'         python eye_tracking_service.py --port 5002')
    else:
        install_deps(sys.executable)
        download_model()
        print('\nRun: python eye_tracking_service.py --port 5002')
