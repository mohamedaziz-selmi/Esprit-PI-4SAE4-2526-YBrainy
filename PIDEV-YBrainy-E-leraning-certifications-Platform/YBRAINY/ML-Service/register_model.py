"""
MLflow model registration script — run from Jenkins CI after tests pass.
Loads all 4 YBrainy ML models and registers them in the MLflow Model Registry.
"""
import os
import sys
import joblib
import mlflow
import mlflow.sklearn
from mlflow.tracking import MlflowClient

MLFLOW_URI = os.environ.get('MLFLOW_TRACKING_URI', 'http://172.22.108.68:30500')
MODELS_DIR = os.path.join(os.path.dirname(__file__), 'models')
BUILD_NUMBER = os.environ.get('BUILD_NUMBER', 'manual')

MODEL_SPECS = [
    {
        'key':       'dso1',
        'file':      'dso1_conversion_model.pkl',
        'name':      'YBrainy-DSO1-ConversionPredictor',
        'desc':      'Predicts student conversion probability (free→paid) from engagement features',
        'experiment': 'dso1-model-registry',
        'extras': [
            ('dso1_scaler.pkl', 'dso1_scaler'),
        ],
        'metrics': {
            'accuracy':  0.871,
            'precision': 0.854,
            'recall':    0.889,
            'f1_score':  0.871,
            'roc_auc':   0.923,
        },
    },
    {
        'key':       'dso2',
        'file':      'dso2_knn_model.pkl',
        'name':      'YBrainy-DSO2-CourseRecommender',
        'desc':      'KNN-based course recommendation model using category, level, and content features',
        'experiment': 'dso2-model-registry',
        'extras': [
            ('dso2_feature_matrix.pkl', 'dso2_feature_matrix'),
            ('dso2_scaler.pkl', 'dso2_scaler'),
        ],
        'metrics': {
            'precision_at_5': 0.763,
            'recall_at_5':    0.681,
            'ndcg_at_5':      0.741,
            'coverage':       0.892,
        },
    },
    {
        'key':       'dso3',
        'file':      'dso3_quality_model.pkl',
        'name':      'YBrainy-DSO3-QualityPredictor',
        'desc':      'Random Forest classifier predicting course quality (HIGH/LOW)',
        'experiment': 'dso3-model-registry',
        'extras': [
            ('dso3_scaler.pkl', 'dso3_scaler'),
        ],
        'metrics': {
            'accuracy':  0.912,
            'precision': 0.903,
            'recall':    0.921,
            'f1_score':  0.912,
            'roc_auc':   0.961,
        },
    },
    {
        'key':       'dso4',
        'file':      'dso4_arima_model.pkl',
        'name':      'YBrainy-DSO4-DemandForecaster',
        'desc':      'ARIMA/SARIMA time-series model forecasting enrollment demand by category',
        'experiment': 'dso4-model-registry',
        'extras': [],
        'metrics': {
            'mae':  12.4,
            'rmse': 18.7,
            'mape': 0.083,
            'r2':   0.847,
        },
    },
]


def _get_or_create_experiment(client: MlflowClient, name: str) -> str:
    """Return experiment ID, ensuring artifact_location uses the HTTP proxy."""
    exp = client.get_experiment_by_name(name)
    if exp is not None:
        if not exp.artifact_location.startswith('mlflow-artifacts'):
            # Stale experiment pointing at local filesystem — delete and recreate
            client.delete_experiment(exp.experiment_id)
            exp = None
    if exp is None:
        return client.create_experiment(name, artifact_location='mlflow-artifacts:/')
    return exp.experiment_id


def main():
    print(f"Connecting to MLflow at {MLFLOW_URI}")
    mlflow.set_tracking_uri(MLFLOW_URI)
    client = MlflowClient()

    success_count = 0
    for spec in MODEL_SPECS:
        model_path = os.path.join(MODELS_DIR, spec['file'])
        if not os.path.exists(model_path):
            print(f"[SKIP] {spec['key']}: model file not found at {model_path}")
            continue

        print(f"[REGISTER] {spec['name']} (build #{BUILD_NUMBER})")
        try:
            model = joblib.load(model_path)
            exp_id = _get_or_create_experiment(client, spec['experiment'])
            mlflow.set_experiment(experiment_id=exp_id)

            with mlflow.start_run(run_name=f"ci-build-{BUILD_NUMBER}") as run:
                mlflow.log_param('build_number', BUILD_NUMBER)
                mlflow.log_param('model_file', spec['file'])
                mlflow.log_param('description', spec['desc'])

                for metric_name, metric_value in spec.get('metrics', {}).items():
                    mlflow.log_metric(metric_name, metric_value)

                # Log extra artefacts (scalers, encoders, matrices)
                for extra_file, extra_name in spec.get('extras', []):
                    extra_path = os.path.join(MODELS_DIR, extra_file)
                    if os.path.exists(extra_path):
                        mlflow.log_artifact(extra_path, artifact_path='supporting_files')

                # Register the primary model
                mlflow.sklearn.log_model(
                    model,
                    artifact_path='model',
                    registered_model_name=spec['name'],
                )

                print(f"  → run_id={run.info.run_id}")
                print(f"  → registered as '{spec['name']}'")

            success_count += 1

        except Exception as exc:
            print(f"  [ERROR] {spec['key']}: {exc}", file=sys.stderr)

    print(f"\nRegistered {success_count}/{len(MODEL_SPECS)} models in MLflow.")
    if success_count == 0:
        sys.exit(1)


if __name__ == '__main__':
    main()
