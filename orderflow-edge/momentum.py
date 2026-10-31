"""Opening-range breakout and VWAP pullback: the momentum day-trading style Umar Ashraf teaches publicly
(opening range, VWAP, 9/20 EMA trend, high relative volume, stops under structure, ~2R targets),
adapted to 24/7 crypto by anchoring the "open" to a fixed UTC time (default: the US stock open, 13:30 UTC).

This is an interpretation of publicly taught concepts, not anyone's exact rules.

Strategies (5-minute bars, long by default, flat by the end of the session):
  orb            First `orb_min` minutes after the open define the range. Long when a 5-min bar closes above the
                 range high, above session VWAP, with relative volume >= rvol. Stop: range low (or mid).
                 Target: R multiple of the risk.
  vwap_pullback  Trend day: 9 EMA > 20 EMA and price above VWAP. Price pulls back to touch VWAP or the 20 EMA,
                 then a bar closes back above the 9 EMA with rvol >= rvol. Stop: the pullback's low. Target: R multiple.
"""
from dataclasses import dataclass

import numpy as np
import pandas as pd

from backtest import Costs, Result


@dataclass
class MParams:
    open_utc: str = "13:30"   # session open (US stocks); "00:00" = crypto daily open
    orb_min: int = 15         # opening range length
    rvol: float = 1.5         # relative volume needed (vs same time of day, previous days)
    r_mult: float = 2.0       # target = r_mult × risk
    stop: str = "low"         # orb: "low" or "mid" of the range
    session_h: int = 6        # flat this many hours after the open


def five_min(b1: pd.DataFrame) -> pd.DataFrame:
    g = b1.resample("5min")
    b = pd.DataFrame({"open": g["open"].first(), "high": g["high"].max(), "low": g["low"].min(), "close": g["close"].last(),
                      "volume": g["volume"].sum(), "notional": g["notional"].sum(), "delta": g["delta"].sum()}).dropna(subset=["close"])
    b["ema9"] = b["close"].ewm(span=9, adjust=False).mean()
    b["ema20"] = b["close"].ewm(span=20, adjust=False).mean()
    # Relative volume: this bar vs the average of the same 5-minute slot over the previous 10 days (past only).
    slot = b.index.hour * 12 + b.index.minute // 5
    b["slot"] = slot
    avg = b.groupby("slot")["volume"].transform(lambda v: v.shift(1).rolling(10, min_periods=3).mean())
    b["rvol"] = b["volume"] / avg
    return b


def _sessions(b: pd.DataFrame, p: MParams):
    hh, mm = map(int, p.open_utc.split(":"))
    for day, d in b.groupby(b.index.floor("D")):
        start = day + pd.Timedelta(hours=hh, minutes=mm)
        s = b[(b.index >= start) & (b.index < start + pd.Timedelta(hours=p.session_h))]
        if len(s) < p.orb_min // 5 + 3:
            continue
        s = s.copy()
        # Session VWAP, anchored at the open.
        s["vwap"] = s["notional"].cumsum() / s["volume"].cumsum().replace(0, np.nan)
        yield s


def _trade(s: pd.DataFrame, i: int, entry: float, stop: float, target: float, costs: Costs) -> tuple[dict, int]:
    """Enter at bar i's open; exit at stop/target (stop first if both in one bar) or the session's last close."""
    c = 2 * (costs.fee + costs.slippage)
    for j in range(i, len(s)):
        lo, hi = s["low"].iat[j], s["high"].iat[j]
        if lo <= stop:
            return {"entry_time": s.index[i], "exit_time": s.index[j], "side": 1, "entry": entry, "exit": stop, "why": "stop",
                    "ret": (stop - entry) / entry - c}, j
        if hi >= target:
            return {"entry_time": s.index[i], "exit_time": s.index[j], "side": 1, "entry": entry, "exit": target, "why": "target",
                    "ret": (target - entry) / entry - c}, j
    px = s["close"].iat[-1]
    return {"entry_time": s.index[i], "exit_time": s.index[-1], "side": 1, "entry": entry, "exit": px, "why": "session end",
            "ret": (px - entry) / entry - c}, len(s) - 1


def run_momentum(b5: pd.DataFrame, name: str, p: MParams, costs: Costs) -> Result:
    rows = []
    n_or = p.orb_min // 5
    for s in _sessions(b5, p):
        if name == "orb":
            rng = s.iloc[:n_or]
            hi, lo = rng["high"].max(), rng["low"].min()
            stop = lo if p.stop == "low" else (hi + lo) / 2
            for i in range(n_or, len(s) - 1):
                r = s.iloc[i]
                if r["close"] > hi and r["close"] > r["vwap"] and r["rvol"] >= p.rvol:
                    entry = s["open"].iat[i + 1]
                    if entry <= stop:
                        break
                    tr, _ = _trade(s, i + 1, entry, stop, entry + p.r_mult * (entry - stop), costs)
                    rows.append(tr)
                    break                                   # one ORB trade per session
        elif name == "vwap_pullback":
            i, touched, pull_low = 3, False, np.inf
            while i < len(s) - 1:
                r = s.iloc[i]
                trend = r["ema9"] > r["ema20"] and r["close"] > r["vwap"]
                if r["low"] <= max(r["vwap"], r["ema20"]) and r["ema9"] > r["ema20"]:
                    touched, pull_low = True, min(pull_low, r["low"])
                elif touched and trend and r["close"] > r["ema9"] and r["rvol"] >= p.rvol:
                    entry = s["open"].iat[i + 1]
                    stop = pull_low
                    if entry > stop:
                        tr, j = _trade(s, i + 1, entry, stop, entry + p.r_mult * (entry - stop), costs)
                        rows.append(tr)
                        i = j
                    touched, pull_low = False, np.inf
                if not (r["ema9"] > r["ema20"]):
                    touched, pull_low = False, np.inf
                i += 1
        else:
            raise ValueError(name)
    return Result(name, pd.DataFrame(rows))


MGRID = [MParams(open_utc=o, orb_min=m, rvol=v, r_mult=r, stop=st)
         for o in ("13:30", "00:00") for m in (15, 30) for v in (1.0, 1.5, 2.0) for r in (1.5, 2.0, 3.0) for st in ("low", "mid")]

MOMENTUM = ["orb", "vwap_pullback"]


def walk_forward_m(b5: pd.DataFrame, name: str, costs: Costs, split: float = 2 / 3, min_trades: int = 10) -> dict:
    cut = b5.index[int(len(b5) * split)].floor("D")
    ins, oos = b5[b5.index < cut], b5[b5.index >= cut]
    grid = MGRID if name == "orb" else [p for p in MGRID if p.stop == "low" and p.orb_min == 15]
    best, best_e = None, -np.inf
    for p in grid:
        r = run_momentum(ins, name, p, costs)
        if len(r.trades) < min_trades:
            continue
        e = r.trades["ret"].mean()
        if e > best_e:
            best, best_e = p, e
    if best is None:
        return {"strategy": name, "note": f"fewer than {min_trades} in-sample trades for every setting"}
    return {"strategy": name, "params": best, "in_sample": run_momentum(ins, name, best, costs).stats(),
            "out_of_sample": run_momentum(oos, name, best, costs), "split": cut}
