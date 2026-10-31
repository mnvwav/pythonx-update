"""Can the daily trend rule be improved? Portfolio-level variants, all long-only, all with fixed textbook settings
(nothing is tuned, so there is nothing to overfit), compared over the full history and the most recent 40%.

  python portfolio.py BTCUSDT ETHUSDT SOLUSDT BNBUSDT XRPUSDT DOGEUSDT ADAUSDT LINKUSDT LTCUSDT TRXUSDT --years 5

Variants:
  buy_hold       equal weight in every coin, always invested
  ma100          each coin: its share when the close is above its 100-day average, else cash   (the current rule)
  ensemble       each coin's share × fraction of its 50/100/150/200-day averages it's above (0, ¼, ½, ¾, 1)
  ensemble_vol   ensemble, then sized by risk: weight × min(1.5, 60% / the coin's 60-day volatility)
  ensemble_btc   ensemble, but altcoins only while BTC is above its own 100-day average
  rotation       every Monday hold the 3 coins with the best 60-day return that are also above their 100-day
                 average, ⅓ each (cash for empty slots)
  combo          ensemble_vol + BTC filter

Decisions at the daily close are held from the next day's open; every change in weight pays the trading cost.
"""
import argparse
from pathlib import Path

import numpy as np
import pandas as pd

from trend import fetch_daily, stats


def panel(data: dict[str, pd.DataFrame], col: str) -> pd.DataFrame:
    return pd.DataFrame({s: d[col] for s, d in data.items()}).sort_index()


def weights(close: pd.DataFrame, variant: str, btc: str | None) -> pd.DataFrame:
    n = close.shape[1]
    listed = close.notna()
    base = listed.astype(float) / n
    above = lambda k: (close > close.rolling(k, min_periods=k).mean()).astype(float)
    ens = sum(above(k) for k in (50, 100, 150, 200)) / 4
    vol = close.pct_change().rolling(60, min_periods=30).std() * np.sqrt(365)
    vol_scale = (0.60 / vol).clip(upper=1.5).fillna(0)
    btc_on = above(100)[btc] if btc in close else pd.Series(1.0, index=close.index)
    alt_mask = pd.DataFrame({c: (1.0 if c == btc else btc_on) for c in close.columns}, index=close.index)

    if variant == "buy_hold":
        w = base
    elif variant == "ma100":
        w = base * above(100)
    elif variant == "ensemble":
        w = base * ens
    elif variant == "ensemble_vol":
        w = base * ens * vol_scale
    elif variant == "ensemble_btc":
        w = base * ens * alt_mask
    elif variant == "combo":
        w = base * ens * vol_scale * alt_mask
    elif variant == "rotation":
        mom = close / close.shift(60) - 1
        ok = mom.where(above(100) > 0)
        w = pd.DataFrame(0.0, index=close.index, columns=close.columns)
        rank = ok.rank(axis=1, ascending=False)
        w[rank <= 3] = 1 / 3
        w[close.index.dayofweek != 0] = np.nan
        w = w.ffill().fillna(0)                                        # rebalanced on Mondays
    else:
        raise ValueError(variant)
    w = w.fillna(0)
    total = w.sum(axis=1)
    return w.div(total.where(total > 1, 1), axis=0)                    # never more than 100% invested


def run(opens: pd.DataFrame, w: pd.DataFrame, cost: float) -> pd.Series:
    held = w.shift(1).fillna(0)                                        # decided at close t, held from open t+1
    r = (opens.shift(-1) / opens - 1).fillna(0)
    turnover = held.diff().abs().sum(axis=1).fillna(held.sum(axis=1))
    return (held * r).sum(axis=1) - turnover * cost


VARIANTS = ["buy_hold", "ma100", "ensemble", "ensemble_vol", "ensemble_btc", "rotation", "combo"]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("symbols", nargs="+")
    ap.add_argument("--years", type=int, default=5)
    ap.add_argument("--fee", type=float, default=0.001)
    ap.add_argument("--slippage", type=float, default=0.0005)
    ap.add_argument("--recent", type=float, default=0.4, help="share of history shown as the 'recent' period")
    ap.add_argument("--out", default="portfolio.md")
    ap.add_argument("--offline", action="store_true")
    a = ap.parse_args()
    cost = a.fee + a.slippage

    data = {}
    for s in a.symbols:
        f = Path("data/daily") / f"{s}.csv"
        data[s] = pd.read_csv(f, index_col=0, parse_dates=True) if (a.offline or f.exists()) and f.exists() else fetch_daily(s, a.years)
    close, opens = panel(data, "close"), panel(data, "open")
    btc = next((s for s in a.symbols if s.startswith("BTC")), None)
    start = close.index[200]                                           # after the longest average has warmed up
    cut = close.index[int(len(close) * (1 - a.recent))]

    L = ["# Trend portfolio variants", "",
         f"{len(a.symbols)} coins: {', '.join(a.symbols)}. Cost {cost*100:.2f}% per unit of weight traded. Long only. "
         "No settings were tuned (textbook values), so the full period is as fair as the recent one.", "",
         f"| variant | full {start:%Y-%m}→ total% | CAGR% | maxDD% | sharpe | recent {cut:%Y-%m}→ total% | CAGR% | maxDD% | sharpe | avg invested |",
         "|---|---|---|---|---|---|---|---|---|---|"]
    for v in VARIANTS:
        w = weights(close, v, btc)
        r = run(opens, w, cost)
        full, rec = stats(r[r.index >= start]), stats(r[r.index >= cut])
        inv = w[w.index >= start].sum(axis=1).mean()
        L.append(f"| {v} | {full['total%']} | {full['CAGR%']} | {full['maxDD%']} | {full['sharpe']} | "
                 f"{rec['total%']} | {rec['CAGR%']} | {rec['maxDD%']} | {rec['sharpe']} | {inv:.0%} |")
    L += ["", "Pick on **sharpe and max drawdown in both periods**, not on total return: big totals usually come "
          "from one lucky coin and don't repeat. Not financial advice; paper-trade first."]
    Path(a.out).write_text("\n".join(L))
    print(f"wrote {a.out}")


if __name__ == "__main__":
    main()
