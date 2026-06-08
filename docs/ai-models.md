# AI and ML Artifacts

The ESPRIT guide requires trained AI models and large datasets to stay outside Git. This repository follows that rule.

## What is excluded

The following artifact types are ignored by `.gitignore`:

```text
*.pkl
*.joblib
*.h5
*.keras
*.pt
*.pth
*.onnx
*.tflite
*.bin
models/
artifacts/
datasets/*.csv
```

## Expected external hosting

Model and dataset artifacts should be hosted on one of:

| Artifact | Recommended host |
| --- | --- |
| Python models (`.pkl`, `.joblib`) | Google Drive, Kaggle, or GitHub Releases |
| Deep learning models | Hugging Face Hub or GitHub Releases |
| Public datasets | Kaggle, Hugging Face Datasets, or Google Drive |
| Demo notebooks | Google Colab |

## Local restoration workflow

After downloading model artifacts, place them back in their original module-specific paths, for example:

```text
PIDEV-YBrainy-E-leraning-certifications-Platform/YBRAINY/ML-Service/models/
PIDEV-YBrainy-E-leraning-certifications-Platform/payment/Dynamic pricing model/artifacts/
PIDEV-YBrainy-E-leraning-certifications-Platform/payment/Scenario Simulator/artifacts/
PIDEV-YBrainy-E-leraning-certifications-Platform/ybrainy events/ml-events-service/models/
```

If a release is created for the public repository, add the release URL here:

```text
MODEL_BASE_URL=replace_with_model_release_or_storage_url
```

## Reproducibility rule

The web platform can be reviewed through the documented Docker and Angular startup flow. AI features that depend on external provider keys or trained artifacts are documented as optional reproducibility steps so the public repository never exposes credentials or heavy model files.
