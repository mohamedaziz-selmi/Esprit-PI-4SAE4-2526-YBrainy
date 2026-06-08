#!/usr/bin/env python3
import os, subprocess, sys, urllib.request
from pathlib import Path

CONDA_ENV = "ybrainy-tts"
PYTHON_VERSION = "3.10"

models = {
    'face_landmarker.task': 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task',
    'hair_segmenter.tflite': 'https://storage.googleapis.com/mediapipe-models/hair_segmenter/hair_segmenter/float32/latest/hair_segmenter.tflite',
    'selfie_multiclass_256x256.tflite': 'https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass/float32/latest/selfie_multiclass_256x256.tflite'
}

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
    os.environ['COQUI_TOS_AGREED'] = '1'
    base_flag = ['--disable-pip-version-check']

    # Install everything except TTS first (open with utf-8-sig to strip BOM)
    other_reqs = [l.strip() for l in open('requirements.txt', encoding='utf-8-sig') if l.strip() and not l.strip().lower().startswith('tts')]
    if other_reqs:
        subprocess.check_call([python_exe, '-m', 'pip', 'install'] + base_flag + other_reqs)

    # TTS build requires Cython to be pre-installed
    print('Installing Cython (TTS build dependency)...')
    subprocess.check_call([python_exe, '-m', 'pip', 'install'] + base_flag + ['Cython'])

    # TTS's setup.py opens README.md without explicit encoding which crashes on Windows.
    # Workaround: download the tarball, patch setup.py, install from source.
    import tarfile, urllib.request, tempfile, shutil

    tts_url = 'https://files.pythonhosted.org/packages/source/T/TTS/TTS-0.22.0.tar.gz'
    tts_tar = Path('TTS-0.22.0.tar.gz')

    if not tts_tar.exists():
        print('Downloading TTS-0.22.0 source...')
        urllib.request.urlretrieve(tts_url, tts_tar)

    print('Extracting TTS source...')
    extract_dir = Path('_tts_build')
    if extract_dir.exists():
        shutil.rmtree(extract_dir)
    with tarfile.open(tts_tar) as t:
        t.extractall(extract_dir)

    tts_setup = extract_dir / 'TTS-0.22.0' / 'setup.py'
    print('Patching TTS setup.py for Windows compatibility...')
    content = tts_setup.read_text(encoding='utf-8')
    # Fix 1: README.md open fails because setuptools execs setup.py with __file__="<string>",
    # making cwd wrong. Replace the block with a safe fallback.
    content = content.replace(
        'with open("README.md", "r", encoding="utf-8") as readme_file:\n    README = readme_file.read()',
        'README = ""  # patched: README.md open skipped for Windows compatibility'
    )
    # Also handle the indented version
    content = content.replace(
        'with open("README.md", "r", encoding="utf-8") as readme_file:\n        README = readme_file.read()',
        'README = ""  # patched: README.md open skipped for Windows compatibility'
    )
    # Fix 2: Remove the Python version gate (we are in a 3.10 conda env, this is safe)
    content = content.replace(
        'if Version(python_version) < Version("3.9") or Version(python_version) >= Version("3.12"):\n    raise RuntimeError("TTS requires python >= 3.9 and < 3.12 " "but your Python version is {}".format(sys.version))',
        '# version check patched out by setup.py'
    )
    # Fix 3: Remove the Cython extension — requires MSVC on Windows; TTS has a pure Python fallback
    content = content.replace(
        'exts = [\n    Extension(\n        name="TTS.tts.utils.monotonic_align.core",\n        sources=["TTS/tts/utils/monotonic_align/core.pyx"],\n    )\n]',
        'exts = []  # patched: Cython extension skipped (no MSVC), pure Python fallback used'
    )
    tts_setup.write_text(content, encoding='utf-8')

    print('Installing patched TTS...')
    tts_src = str(extract_dir / 'TTS-0.22.0')
    subprocess.check_call([python_exe, '-m', 'pip', 'install'] + base_flag + ['--no-build-isolation', tts_src])

    shutil.rmtree(extract_dir)
    if tts_tar.exists():
        tts_tar.unlink()

def download_models():
    for name, url in models.items():
        if Path(name).exists():
            print(f'{name} already exists')
            continue
        print(f'Downloading {name}...')
        try:
            urllib.request.urlretrieve(url, name)
            print(f'Downloaded {name}')
        except Exception as e:
            print(f'WARNING: Could not download {name}: {e}')
            print(f'  URL may be deprecated. Download manually if needed: {url}')

if __name__ == '__main__':
    major, minor = sys.version_info[:2]

    if major == 3 and minor >= 12:
        print(f'Python {major}.{minor} detected — TTS requires Python <= 3.11.')
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
        download_models()
        print(f'\nSetup complete.')
        print(f'To run:  conda activate {CONDA_ENV}')
        print(f'         python talking_head_tts_server.py --port 8765')
        print(f'Note: XTTS v2 model (~2GB) will download on first run.')
    else:
        install_deps(sys.executable)
        download_models()
        print('\nRun: python talking_head_tts_server.py --port 8765')
        print('Note: XTTS v2 model (~2GB) downloads on first run')
