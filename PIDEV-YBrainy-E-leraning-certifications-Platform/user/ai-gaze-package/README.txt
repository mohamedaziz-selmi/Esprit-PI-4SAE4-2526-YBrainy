# YBrainy AI Services

## Gaze Tracking (Port 5002)
`ash
cd ai-gaze-package
python setup.py
python eye_tracking_service.py --port 5002
`

## Talking Head TTS (Port 8765)
`ash
cd ai-talking-head-package
python setup.py
python talking_head_tts_server.py --port 8765
`

## Requirements
- Python 3.9-3.11
- pip
- 8GB RAM (16GB for TTS)
- Internet (for model downloads)

## Troubleshooting
- Port busy? Use --port 5003 instead
- Setup fails? Run: python -m pip install --upgrade pip
