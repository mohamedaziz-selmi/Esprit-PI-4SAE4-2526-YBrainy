import sys
import os

# Ensure the predict-service root is on the path so `import app` resolves correctly.
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
