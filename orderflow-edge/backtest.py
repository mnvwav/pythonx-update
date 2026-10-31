"""Order-flow strategies and an honest backtest.

Rules for honesty:
  - Signals use only data up to the close of bar t; entries fill at the OPEN of bar t+1.
  - Exits: stop and target on later bars' high/low (if both are hit in the same bar, the stop counts: worst case),
    or a time limit. One position at a time.
  - Costs on every entry and exit: fee + slippage per side (CoinDCX-like defaults, change them in run.py).
  - Parameters are chosen on the first part of the data (in-sample) and judged only on the rest (out-of-sample).
"""
from dataclasses import dataclass, field

import numpy as np
import pandas as pd


@dataclass
class Costs:
    fee: float = 0.001        # per side (0.1%). Check your CoinDCX fee tier.
    slippage: float = 0.0002  # per side


@dataclass
class Params:
    k: float = 2.0            # z-score threshold
    stop_atr: float = 1.5
    target_atr: float = 3.0
    max_bars: int = 60


@dataclass
class Result:
    name: str
    trades: pd.DataFrame = field(default_factory=pd.DataFrame)

    def stats(self) -> dict:
        t = self.trades
        if t.empty:
            return {"strategy": self.name, "trades": 0}
        r = t["ret"]
        eq = (1 + r).cumprod()
        dd = (eq / eq.cummax() - 1).min()
        wins, losses = r[r > 0], r[r <= 0]
        days = max(1.0, (t["exit_time"].max() - t["entry_time"].min()).total_seconds() / 86400)
        return {
            "strategy": self.name, "trades": len(t), "trades/day": round(len(t) / days, 2),
            "win%": round(100 * len(wins) / len(t), 1),
            "avg win%": round(100 * wins.mean(), 3) if len(wins) else 0.0,
            "avg loss%": round(100 * losses.mean(), 3) if len(losses) else 0.0,
            "expectancy%": round(100 * r.mean(), 4),
            "profit factor": round(wins.sum() / -losses.sum(), 2) if losses.sum() < 0 else float("inf"),
            "total%": round(100 * (eq.iloc[-1] - 1), 2), "max DD%": round(100 * dd, 2),
        }


def signals(f: pd.DataFrame, name: str, p: Params) -> pd.Series:
    """+1 long, -1 short, 0 none, decided at bar close."""
    s = pd.Series(0, index=f.index)
    if name == "delta_breakout":
        s[(f["delta_z"] > p.k) & (f["close"] > f["hi_30"])] = 1
        s[(f["delta_z"] < -p.k) & (f["close"] < f["lo_30"])] = -1
    elif name == "absorption":
        s[(f["delta_z"] < -p.k) & (f["ret_5"] >= 0)] = 1
        s[(f["delta_z"] > p.k) & (f["ret_5"] <= 0)] = -1
    elif name == "cvd_divergence":
        s[f["div_long"]] = 1
        s[f["div_short"]] = -1
    elif name == "whale_flow":
        s[f["big_z"] > p.k] = 1
        s[f["big_z"] < -p.k] = -1
    else:
        raise ValueError(name)
    return s


STRATEGIES = ["delta_breakout", "absorption", "cvd_divergence", "whale_flow"]


def run(f: pd.DataFrame, name: str, p: Params, costs: Costs, long_only: bool = True) -> Result:
    sig = signals(f, name, p).to_numpy()
    o, h, l, c, atr = (f[x].to_numpy() for x in ("open", "high", "low", "close", "atr"))
    idx = f.index
    n = len(f)
    rows = []
    i = 0
    side_cost = costs.fee + costs.slippage
    while i < n - 1:
        side = sig[i]
        if side == 0 or (long_only and side < 0) or not np.isfinite(atr[i]) or atr[i] <= 0:
            i += 1
            continue
        entry_i = i + 1
        entry = o[entry_i]
        stop = entry - side * p.stop_atr * atr[i]
        target = entry + side * p.target_atr * atr[i]
        exit_px, exit_i, why = c[min(n - 1, entry_i + p.max_bars)], min(n - 1, entry_i + p.max_bars), "time"
        for j in range(entry_i, min(n, entry_i + p.max_bars + 1)):
            hit_stop = l[j] <= stop if side > 0 else h[j] >= stop
            hit_tgt = h[j] >= target if side > 0 else l[j] <= target
            if hit_stop:                      # worst case first
                exit_px, exit_i, why = stop, j, "stop"; break
            if hit_tgt:
                exit_px, exit_i, why = target, j, "target"; break
        gross = side * (exit_px - entry) / entry
        rows.append({"entry_time": idx[entry_i], "exit_time": idx[exit_i], "side": int(side), "entry": entry,
                     "exit": exit_px, "why": why, "ret": gross - 2 * side_cost})
        i = exit_i + 1
    return Result(name, pd.DataFrame(rows))


GRID = [Params(k=k, stop_atr=s, target_atr=t, max_bars=m)
        for k in (2.0, 2.5, 3.0) for s in (1.0, 2.0) for t in (2.0, 4.0) for m in (30, 120)]


def walk_forward(f: pd.DataFrame, name: str, costs: Costs, split: float = 2 / 3, long_only: bool = True,
                 min_trades: int = 20) -> dict:
    """Pick the best parameters in-sample (by expectancy, needing enough trades), then test them out-of-sample."""
    cut = f.index[int(len(f) * split)]
    ins, oos = f[f.index < cut], f[f.index >= cut]
    best, best_e = None, -np.inf
    for p in GRID:
        r = run(ins, name, p, costs, long_only)
        if len(r.trades) < min_trades:
            continue
        e = r.trades["ret"].mean()
        if e > best_e:
            best, best_e = p, e
    if best is None:
        return {"strategy": name, "note": f"fewer than {min_trades} in-sample trades for every setting"}
    return {"strategy": name, "params": best, "in_sample": run(ins, name, best, costs, long_only).stats(),
            "out_of_sample": run(oos, name, best, costs, long_only), "split": cut}
