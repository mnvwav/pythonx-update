"""Daily trend-following: the most consistently documented edge in crypto. Few trades, so fees barely matter.

  python trend.py BTCUSDT ETHUSDT SOLUSDT BNBUSDT XRPUSDT --years 5

Downloads daily candles from Binance's public archive (small: one file per month), then tests three classic
long-only rules on each coin and on an equal-weight portfolio of all of them:

  donchian   buy when the close breaks the N-day high; sell when it breaks the M-day low      (turtle-style)
  ma_trend   hold while the close is above its N-day moving average
  tsmom      hold while the last N-day return is positive (time-series momentum), checked weekly

Honesty:
  - decisions at the daily close, filled at the next day's open; fee + slippage on every buy and sell
  - parameters chosen on the first 60% of history, judged on the last 40% only
  - every parameter in the grid is shown for the test period, so a lucky single setting can't hide
  - compared with simply buying and holding over the same test period
"""
import argparse
import datetime as dt
import io
import ssl
import time
import urllib.request
import zipfile
from pathlib import Path

import numpy as np
import pandas as pd

try:
    import certifi
    SSL = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    SSL = ssl.create_default_context()

URL = "https://data.binance.vision/data/spot/monthly/klines/{s}/1d/{s}-1d-{y}-{m:02d}.zip"


def fetch_daily(symbol: str, years: int, root: Path = Path("data")) -> pd.DataFrame:
    out = root / "daily" / f"{symbol}.csv"
    out.parent.mkdir(parents=True, exist_ok=True)
    frames = []
    today = dt.date.today()
    y, m = today.year - years, today.month
    while (y, m) < (today.year, today.month):
        url = URL.format(s=symbol, y=y, m=m)
        for attempt in range(4):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "orderflow-edge/1.0"})
                with urllib.request.urlopen(req, timeout=60, context=SSL) as r:
                    raw = r.read()
                with zipfile.ZipFile(io.BytesIO(raw)) as z, z.open(z.namelist()[0]) as f:
                    frames.append(pd.read_csv(f, header=None, usecols=range(6)))
                break
            except urllib.error.HTTPError as e:
                if e.code == 404:
                    break           # coin not listed yet that month
                time.sleep(2 ** attempt)
            except Exception as e:
                if attempt == 3:
                    print(f"   skipped {symbol} {y}-{m:02d}: {e}")
                time.sleep(2 ** attempt)
        m += 1
        if m > 12:
            y, m = y + 1, 1
    if not frames:
        raise SystemExit(f"no daily data for {symbol}")
    d = pd.concat(frames, ignore_index=True)
    d.columns = ["ts", "open", "high", "low", "close", "volume"]
    d = d[pd.to_numeric(d["ts"], errors="coerce").notna()].astype(float)
    d["ts"] = np.where(d["ts"] > 1e14, d["ts"] / 1000, d["ts"])      # microsecond files -> ms
    d.index = pd.to_datetime(d["ts"], unit="ms", utc=True).dt.floor("D")
    d = d[~d.index.duplicated()].sort_index()[["open", "high", "low", "close", "volume"]]
    d.to_csv(out)
    print(f"{symbol}: {len(d)} days, {d.index[0]:%Y-%m-%d} → {d.index[-1]:%Y-%m-%d}")
    return d


def position(d: pd.DataFrame, rule: str, a: int, b: int) -> pd.Series:
    """1 = hold, 0 = cash, decided at the close of each day (used from the next day's open)."""
    c = d["close"]
    if rule == "donchian":
        hi, lo = d["high"].rolling(a).max().shift(1), d["low"].rolling(b).min().shift(1)
        pos, state = [], 0
        for px, h, l in zip(c, hi, lo):
            if state == 0 and h == h and px > h:
                state = 1
            elif state == 1 and l == l and px < l:
                state = 0
            pos.append(state)
        return pd.Series(pos, index=d.index)
    if rule == "ma_trend":
        return (c > c.rolling(a).mean()).astype(int)
    if rule == "tsmom":
        weekly = (c / c.shift(a) - 1 > 0).astype(int)
        return weekly.where(d.index.dayofweek == 0).ffill().fillna(0).astype(int)   # re-checked on Mondays
    raise ValueError(rule)


def equity(d: pd.DataFrame, pos: pd.Series, cost: float) -> pd.Series:
    """Daily returns of following `pos`: held positions earn open-to-open moves; each change pays `cost`."""
    held = pos.shift(1).fillna(0)                              # decision at close t -> held from open t+1
    r_open = d["open"].shift(-1) / d["open"] - 1               # open t -> open t+1
    trades = held.diff().abs().fillna(held)
    return (held * r_open - trades * cost).fillna(0)


GRID = {
    "donchian": [(a, b) for a in (20, 55, 100) for b in (10, 20, 50) if b < a],
    "ma_trend": [(a, 0) for a in (20, 50, 100, 150, 200)],
    "tsmom": [(a, 0) for a in (14, 30, 60, 90, 180)],
}


def stats(r: pd.Series) -> dict:
    if len(r) < 2:
        return {}
    eq = (1 + r).cumprod()
    yrs = len(r) / 365
    cagr = eq.iloc[-1] ** (1 / yrs) - 1 if yrs > 0 else 0
    vol = r.std() * np.sqrt(365)
    return {"total%": round(float(100 * (eq.iloc[-1] - 1)), 1), "CAGR%": round(float(100 * cagr), 1),
            "maxDD%": round(float(100 * (eq / eq.cummax() - 1).min()), 1),
            "sharpe": round(float(r.mean() * 365 / vol), 2) if vol > 0 else 0.0}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("symbols", nargs="+")
    ap.add_argument("--years", type=int, default=5)
    ap.add_argument("--fee", type=float, default=0.001)
    ap.add_argument("--slippage", type=float, default=0.0005)
    ap.add_argument("--split", type=float, default=0.6)
    ap.add_argument("--out", default="trend.md")
    ap.add_argument("--offline", action="store_true", help="use data/daily/*.csv already downloaded")
    a = ap.parse_args()
    cost = a.fee + a.slippage

    data = {}
    for s in a.symbols:
        f = Path("data/daily") / f"{s}.csv"
        data[s] = pd.read_csv(f, index_col=0, parse_dates=True) if a.offline and f.exists() else fetch_daily(s, a.years)

    L = ["# Daily trend-following report", "",
         f"Cost {cost*100:.2f}% per buy and per sell. Long only. Parameters chosen on the first {int(a.split*100)}% "
         f"of each coin's history; everything below is the remaining {100-int(a.split*100)}% (out-of-sample).", ""]
    portfolio = {}
    for s, d in data.items():
        cut = d.index[int(len(d) * a.split)]
        test = d.index >= cut
        bh = d["open"].shift(-1) / d["open"] - 1
        L += [f"## {s}  (test period {cut:%Y-%m-%d} → {d.index[-1]:%Y-%m-%d})", "",
              "Buy & hold: " + ", ".join(f"{k} {v}" for k, v in stats(bh[test].fillna(0)).items()), "",
              "| rule | chosen on training | test total% | test CAGR% | test maxDD% | test sharpe | trades/yr | every setting's test total% |",
              "|---|---|---|---|---|---|---|---|"]
        for rule, grid in GRID.items():
            best, best_s = None, -np.inf
            for p in grid:
                r = equity(d, position(d, rule, *p), cost)
                tr = r[~test]
                sh = tr.mean() / tr.std() if tr.std() > 0 else -np.inf
                if sh > best_s:
                    best, best_s = p, sh
            pos = position(d, rule, *best)
            r = equity(d, pos, cost)
            st = stats(r[test])
            trades = pos[test].diff().abs().sum() / 2 / (test.sum() / 365)
            every = ", ".join(f"{p[0]}{'/'+str(p[1]) if p[1] else ''}:{stats(equity(d, position(d, rule, *p), cost)[test])['total%']}" for p in grid)
            L.append(f"| {rule} | {best[0]}{'/'+str(best[1]) if best[1] else ''} | {st['total%']} | {st['CAGR%']} | {st['maxDD%']} | "
                     f"{st['sharpe']} | {trades:.1f} | {every} |")
            portfolio.setdefault(rule, []).append(r[test].rename(s))
        portfolio.setdefault("buy_hold", []).append(bh[test].fillna(0).rename(s))
        L.append("")

    L += ["## Equal-weight portfolio of all coins (test periods, daily rebalanced)", "",
          "| strategy | total% | CAGR% | maxDD% | sharpe |", "|---|---|---|---|---|"]
    for rule, parts in portfolio.items():
        p = pd.concat(parts, axis=1).fillna(0).mean(axis=1)
        st = stats(p)
        L.append(f"| {rule} | {st['total%']} | {st['CAGR%']} | {st['maxDD%']} | {st['sharpe']} |")
    L += ["", "## How to read this", "",
          "- A rule is worth paper-trading if, out-of-sample, it beats buy & hold on **max drawdown and sharpe** with "
          "most settings in the last column positive — not just the chosen one.",
          "- Trend-following usually earns *less than buy & hold in bull runs* and loses *far less in crashes*; "
          "its value is surviving the bad years.",
          "- India: gains taxed 30% with no loss offset, 1% TDS on sells. Fewer trades = less of both.",
          "- Not financial advice. Paper-trade first, then small size."]
    Path(a.out).write_text("\n".join(L))
    print(f"wrote {a.out}")


if __name__ == "__main__":
    main()
