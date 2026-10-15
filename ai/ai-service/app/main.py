from fastapi import FastAPI
from pydantic import BaseModel, Field
from typing import List, Optional
from math import erf
import numpy as np

app = FastAPI(title="xCAPITAL AI Service", version="0.3.0")

MODEL_VERSION = "baseline-v1"
RL_BASELINE_VERSION = "rl-baseline-v1"

class PredictRequest(BaseModel):
    returns: List[float] = Field(min_length=30, max_length=50000)
    horizon: int = Field(default=1, ge=1, le=30)

class PredictResponse(BaseModel):
    expected_return: float
    volatility: float
    p_up: float
    confidence: float
    model_version: str
    training_window_start: Optional[str] = None
    training_window_end: Optional[str] = None
    disclaimer: str

class RiskRequest(BaseModel):
    returns: List[float] = Field(min_length=30, max_length=50000)
    horizon: int = Field(default=1, ge=1, le=30)

class RiskResponse(BaseModel):
    volatility: float
    max_drawdown: float
    confidence: float
    regime: str
    model_version: str
    training_window_start: Optional[str] = None
    training_window_end: Optional[str] = None
    disclaimer: str

class EvaluateRequest(BaseModel):
    returns: List[float] = Field(min_length=60, max_length=200000)
    horizon: int = Field(default=1, ge=1, le=30)
    window: int = Field(default=60, ge=30, le=2000)

class EvalModelMetrics(BaseModel):
    model: str
    mae: float
    mse: float
    count: int

class EvalRegimeMetrics(BaseModel):
    regime: str
    model: str
    mae: float
    mse: float
    count: int

class EvaluateResponse(BaseModel):
    horizon: int
    window: int
    models: List[EvalModelMetrics]
    regimes: List[EvalRegimeMetrics]
    model_version: str
    disclaimer: str

class RlBaselineRequest(BaseModel):
    returns: List[List[float]] = Field(min_length=1, max_length=200000)
    risk_target: float = Field(default=0.1, gt=0.0, le=5.0)
    max_turnover: float = Field(default=0.25, ge=0.0, le=1.0)
    current_weights: Optional[List[float]] = None
    cash_weight: float = Field(default=0.0, ge=0.0, le=1.0)

class RlBaselineResponse(BaseModel):
    policy: str
    target_weights: List[float]
    deltas: List[float]
    cash_weight: float
    portfolio_volatility: float
    turnover: float
    model_version: str
    disclaimer: str

@app.post("/v1/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    r = np.array(req.returns, dtype=float)
    mu = float(np.mean(r))
    vol = float(np.std(r, ddof=1))
    if vol <= 1e-12:
        p_up = 0.5 if mu == 0 else (1.0 if mu > 0 else 0.0)
        conf = 0.2
    else:
        z = mu / vol
        p_up = float(0.5 * (1.0 + erf(z / np.sqrt(2.0))))
        conf = float(min(0.9, 0.55 + abs(z) * 0.1))

    return PredictResponse(
        expected_return=mu * req.horizon,
        volatility=vol * np.sqrt(req.horizon),
        p_up=p_up,
        confidence=conf,
        model_version=MODEL_VERSION,
        training_window_start=None,
        training_window_end=None,
        disclaimer="Educational forecast only. Not financial advice."
    )

@app.post("/v1/risk", response_model=RiskResponse)
def risk(req: RiskRequest):
    r = np.array(req.returns, dtype=float)
    vol = float(np.std(r, ddof=1))
    eq = 1.0
    peak = 1.0
    max_dd = 0.0
    for x in r:
        eq *= (1.0 + x)
        if eq > peak:
            peak = eq
        dd = (peak - eq) / peak if peak > 0 else 0.0
        if dd > max_dd:
            max_dd = dd

    sample = len(r)
    conf = float(min(0.9, 0.4 + np.sqrt(sample) / 100.0))
    regime = "HIGH_VOL" if vol > 0.02 else "LOW_VOL"

    return RiskResponse(
        volatility=vol * np.sqrt(req.horizon),
        max_drawdown=float(max_dd),
        confidence=conf,
        regime=regime,
        model_version=MODEL_VERSION,
        training_window_start=None,
        training_window_end=None,
        disclaimer="Educational risk forecast only. Not financial advice."
    )

@app.post("/v1/evaluate", response_model=EvaluateResponse)
def evaluate(req: EvaluateRequest):
    r = np.array(req.returns, dtype=float)
    window = min(req.window, len(r) - req.horizon)
    if window < 30:
        raise ValueError("window too small for evaluation")

    preds = {"mean": [], "momentum": []}
    targets = []
    vols = []

    for i in range(window, len(r) - req.horizon + 1):
        train = r[i - window:i]
        target = float(np.mean(r[i:i + req.horizon]))
        vol = float(np.std(train, ddof=1))
        vols.append(vol)
        targets.append(target)

        preds["mean"].append(float(np.mean(train)))
        k = min(5, len(train))
        preds["momentum"].append(float(np.mean(train[-k:])))

    metrics = []
    for model, p in preds.items():
        err = np.array(p) - np.array(targets)
        mae = float(np.mean(np.abs(err)))
        mse = float(np.mean(err ** 2))
        metrics.append(EvalModelMetrics(model=model, mae=mae, mse=mse, count=len(p)))

    regimes = []
    if vols:
        median_vol = float(np.median(vols))
        for model, p in preds.items():
            low_err = []
            high_err = []
            for idx, pred in enumerate(p):
                err = pred - targets[idx]
                if vols[idx] <= median_vol:
                    low_err.append(err)
                else:
                    high_err.append(err)
            if low_err:
                low_err = np.array(low_err)
                regimes.append(EvalRegimeMetrics(
                    regime="LOW_VOL", model=model,
                    mae=float(np.mean(np.abs(low_err))),
                    mse=float(np.mean(low_err ** 2)),
                    count=len(low_err)
                ))
            if high_err:
                high_err = np.array(high_err)
                regimes.append(EvalRegimeMetrics(
                    regime="HIGH_VOL", model=model,
                    mae=float(np.mean(np.abs(high_err))),
                    mse=float(np.mean(high_err ** 2)),
                    count=len(high_err)
                ))

    return EvaluateResponse(
        horizon=req.horizon,
        window=window,
        models=metrics,
        regimes=regimes,
        model_version=MODEL_VERSION,
        disclaimer="Evaluation is educational. Do not use for live trading."
    )

@app.post("/v1/rl/baseline", response_model=RlBaselineResponse)
def rl_baseline(req: RlBaselineRequest):
    r = np.array(req.returns, dtype=float)
    if r.ndim == 1:
        r = r.reshape(1, -1)
    if r.ndim != 2 or r.shape[1] < 30:
        raise ValueError("returns must be a 2D array with at least 30 samples")

    asset_count = r.shape[0]
    vols = np.std(r, axis=1, ddof=1)
    vols = np.where(vols <= 1e-12, 1e-12, vols)
    inv_vol = 1.0 / vols
    weights = inv_vol / np.sum(inv_vol)

    if req.cash_weight > 0:
        weights = weights * (1.0 - req.cash_weight)

    port_returns = np.dot(weights, r)
    port_vol = float(np.std(port_returns, ddof=1))
    if port_vol > 1e-12:
        scale = min(1.0, req.risk_target / port_vol)
        weights = weights * scale
        port_returns = np.dot(weights, r)
        port_vol = float(np.std(port_returns, ddof=1))

    cash_weight = max(0.0, 1.0 - float(np.sum(weights)))
    deltas = [0.0] * asset_count
    turnover = 0.0

    if req.current_weights is not None:
        if len(req.current_weights) != asset_count:
            raise ValueError("current_weights length mismatch")
        current = np.array(req.current_weights, dtype=float)
        delta = weights - current
        turnover = float(np.sum(np.abs(delta)))
        if req.max_turnover > 0 and turnover > req.max_turnover:
            scale = req.max_turnover / turnover
            delta = delta * scale
            weights = current + delta
            turnover = float(np.sum(np.abs(delta)))
            cash_weight = max(0.0, 1.0 - float(np.sum(weights)))
        deltas = delta.tolist()

    return RlBaselineResponse(
        policy="risk_parity_baseline",
        target_weights=weights.tolist(),
        deltas=deltas,
        cash_weight=cash_weight,
        portfolio_volatility=port_vol,
        turnover=turnover,
        model_version=RL_BASELINE_VERSION,
        disclaimer="Baseline policy for research only. Not financial advice."
    )
