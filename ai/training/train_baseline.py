"""Baseline training/evaluation template.

- Rolling window evaluation
- EWMA volatility

Upgrade: add PyTorch + LSTM/Transformer, Optuna tuning, RL environment.
"""
import argparse
import numpy as np

def ewma_vol(returns: np.ndarray, lam: float = 0.94) -> float:
    var = 0.0
    for r in returns:
        var = lam * var + (1 - lam) * (r*r)
    return float(np.sqrt(var))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", required=True, help="CSV with one column of returns")
    ap.add_argument("--window", type=int, default=252)
    args = ap.parse_args()

    r = np.loadtxt(args.file, delimiter=",")
    if r.ndim > 1:
        r = r[:,0]
    if len(r) < args.window + 10:
        raise SystemExit("Need more data")

    preds, actuals, vols = [], [], []
    for i in range(args.window, len(r)-1):
        hist = r[i-args.window:i]
        preds.append(float(np.mean(hist)))
        vols.append(ewma_vol(hist))
        actuals.append(float(r[i+1]))

    preds = np.array(preds)
    actuals = np.array(actuals)
    mae = float(np.mean(np.abs(preds - actuals)))
    corr = float(np.corrcoef(preds, actuals)[0,1])

    print("MAE:", mae)
    print("Corr:", corr)
    print("Mean EWMA vol:", float(np.mean(vols)))

if __name__ == "__main__":
    main()
