# xCAPITAL AI

This folder contains a statistical baseline for:
- Supervised prediction (returns/volatility)
- Risk estimation (tail risk)
- Inverse-volatility allocation (`/v1/rl/baseline`; this endpoint does not train a reinforcement learning policy)

**Design rule:** AI produces signals + uncertainty. The deterministic math engine enforces constraints.

## Local API regression checks

Use Python 3.12, matching the service image. From the repository root:

```bash
python3.12 -m venv ai/ai-service/.venv
ai/ai-service/.venv/bin/python -m pip install -r ai/ai-service/requirements-test.txt
cd ai/ai-service
.venv/bin/python -m unittest discover -s tests -v
```

These checks exercise the in-process HTTP API with synthetic inputs. They verify the statistical baseline and NumPy compatibility; they do not validate forecasting quality or live financial decisions. The allocation API expects rows of assets, each containing at least 30 return samples.

Baseline policy input expects a 2D returns array (assets x samples) and returns target weights plus deltas.

Example:

```bash
python3 -c 'import json; print(json.dumps({"returns": [[0.01,-0.02,0.005]*10, [0.008,-0.01,0.004]*10], "current_weights": [0.6,0.4]}))' | \
  curl -s http://localhost:8090/v1/rl/baseline \
    -H "Content-Type: application/json" --data-binary @-
```
