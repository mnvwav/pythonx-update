"""Order-flow features on 1-minute bars, built from individual trades.

Per bar:
  open/high/low/close, volume (base), buy_vol / sell_vol (aggressor side), delta = buy_vol - sell_vol,
  cvd (cumulative delta), trades, big_delta (delta of the largest 1% of trades by notional: "whale" flow),
  vwap.
Derived (all use only past data, so nothing peeks into the future):
  delta_z      z-score of 5-minute delta against the previous 24 hours
  big_z        z-score of 15-minute whale delta against the previous 24 hours
  ret_5        5-minute return
  absorb       strong selling (delta_z < -k) while price held (ret_5 >= 0): sellers absorbed by bids; mirrored for buying
  cvd_div      price at a 60-minute low while CVD is not (bullish divergence), and the mirror
  atr          14-bar average true range, for stops
"""
import numpy as np
import pandas as pd


def bars(trades: pd.DataFrame, freq: str = "1min", whale_quantile: float = 0.99) -> pd.DataFrame:
    t = trades.copy()
    t["time"] = pd.to_datetime(t["ts"], unit="ms", utc=True)
    t["notional"] = t["price"] * t["qty"]
    # Aggressor side: is_buyer_maker True -> the buyer was resting, so the taker SOLD.
    sign = np.where(t["is_buyer_maker"], -1.0, 1.0)
    t["buy_vol"] = np.where(sign > 0, t["qty"], 0.0)
    t["sell_vol"] = np.where(sign < 0, t["qty"], 0.0)
    # Whale threshold from a rolling window would be ideal; a single global quantile leaks a little information,
    # so it is computed per day on the PREVIOUS day's trades (first day: its own).
    t["day"] = t["time"].dt.floor("D")
    q = t.groupby("day")["notional"].quantile(whale_quantile)
    q_prev = q.shift(1).fillna(q)
    t["big"] = t["notional"] >= t["day"].map(q_prev)
    t["big_delta"] = np.where(t["big"], sign * t["qty"], 0.0)

    g = t.set_index("time").groupby(pd.Grouper(freq=freq))
    b = pd.DataFrame({
        "open": g["price"].first(), "high": g["price"].max(), "low": g["price"].min(), "close": g["price"].last(),
        "volume": g["qty"].sum(), "buy_vol": g["buy_vol"].sum(), "sell_vol": g["sell_vol"].sum(),
        "trades": g["price"].count(), "big_delta": g["big_delta"].sum(), "notional": g["notional"].sum(),
    })
    b["close"] = b["close"].ffill()
    for c in ("open", "high", "low"):
        b[c] = b[c].fillna(b["close"])
    b = b.fillna({"volume": 0, "buy_vol": 0, "sell_vol": 0, "trades": 0, "big_delta": 0, "notional": 0})
    b["vwap"] = (b["notional"] / b["volume"].replace(0, np.nan)).fillna(b["close"])
    b["delta"] = b["buy_vol"] - b["sell_vol"]
    b["cvd"] = b["delta"].cumsum()
    return b.dropna(subset=["close"])


def _z(x: pd.Series, window: int) -> pd.Series:
    m = x.rolling(window, min_periods=window // 2).mean().shift(1)
    s = x.rolling(window, min_periods=window // 2).std().shift(1)
    return (x - m) / s.replace(0, np.nan)


def add_features(b: pd.DataFrame, absorb_k: float = 2.0) -> pd.DataFrame:
    f = b.copy()
    day = 24 * 60
    f["delta_5"] = f["delta"].rolling(5).sum()
    f["delta_z"] = _z(f["delta_5"], day)
    f["big_15"] = f["big_delta"].rolling(15).sum()
    f["big_z"] = _z(f["big_15"], day)
    f["ret_5"] = f["close"].pct_change(5)
    f["hi_30"] = f["high"].rolling(30).max().shift(1)
    f["lo_30"] = f["low"].rolling(30).min().shift(1)
    tr = pd.concat([f["high"] - f["low"], (f["high"] - f["close"].shift()).abs(), (f["low"] - f["close"].shift()).abs()], axis=1).max(axis=1)
    f["atr"] = tr.rolling(14).mean()
    f["absorb_long"] = (f["delta_z"] < -absorb_k) & (f["ret_5"] >= 0)
    f["absorb_short"] = (f["delta_z"] > absorb_k) & (f["ret_5"] <= 0)
    lo_60 = f["low"].rolling(60).min()
    hi_60 = f["high"].rolling(60).max()
    cvd_lo_60 = f["cvd"].rolling(60).min()
    cvd_hi_60 = f["cvd"].rolling(60).max()
    # Price makes the 60-minute extreme now but CVD does not: the move isn't backed by aggressive flow.
    f["div_long"] = (f["low"] <= lo_60) & (f["cvd"] > cvd_lo_60 + 0.25 * (cvd_hi_60 - cvd_lo_60))
    f["div_short"] = (f["high"] >= hi_60) & (f["cvd"] < cvd_hi_60 - 0.25 * (cvd_hi_60 - cvd_lo_60))
    return f
