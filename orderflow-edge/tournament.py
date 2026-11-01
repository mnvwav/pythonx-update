"""Strategy tournament: every contestant gets the same paper capital from the same start date; a leaderboard shows
who is ahead. Positions are computed only from data up to each day's close and held from the next open, with the
trading cost on every change, so the forward results are honest. A 5-year backtest column is shown for context.

  python tournament.py --start 2026-11-01 --capital 8000 > tournament.txt
"""
import argparse

import numpy as np
import pandas as pd

from portfolio import panel, run, weights
from signals import COINS
from trend import fetch_daily, stats

COST = 0.0015


def rsi(c: pd.Series, n: int = 2) -> pd.Series:
    d = c.diff()
    up = d.clip(lower=0).ewm(alpha=1 / n, adjust=False).mean()
    dn = (-d.clip(upper=0)).ewm(alpha=1 / n, adjust=False).mean()
    return 100 - 100 / (1 + up / dn.replace(0, np.nan))


def contestant(close: pd.DataFrame, name: str) -> pd.DataFrame:
    if name in ("combo", "ensemble_vol", "rotation", "buy_hold"):
        return weights(close, name, "BTCUSDT")
    w = pd.DataFrame(0.0, index=close.index, columns=close.columns)
    if name == "btc_hold":
        w["BTCUSDT"] = 1.0
    elif name == "moonshot":
        # All-in on the single strongest coin (60-day return) that is above its 100-day average; re-picked Mondays.
        mom = (close / close.shift(60) - 1).where(close > close.rolling(100).mean())
        # Days when no coin qualifies stay in cash.
        has = mom.notna().any(axis=1)
        best = mom.fillna(-np.inf).idxmax(axis=1).where(has)
        for i, c in enumerate(best):
            if isinstance(c, str):
                w.iat[i, w.columns.get_loc(c)] = 1.0
        w[close.index.dayofweek != 0] = np.nan
        w = w.ffill().fillna(0)
    elif name == "rsi2_dips":
        # Connors RSI-2 on BTC and ETH: in a long-term uptrend, buy when 2-day RSI < 10, sell when price closes
        # above its 5-day average. Half the capital per coin.
        for c in ("BTCUSDT", "ETHUSDT"):
            px = close[c]
            up = px > px.rolling(200).mean()
            entry = up & (rsi(px) < 10)
            exit_ = px > px.rolling(5).mean()
            pos, state = [], 0.0
            for e, x in zip(entry, exit_):
                state = 0.5 if (state == 0 and e) else (0.0 if (state > 0 and x) else state)
                pos.append(state)
            w[c] = pos
    else:
        raise ValueError(name)
    return w.fillna(0)


CONTESTANTS = {
    "combo": "Combo trend (tested winner)",
    "ensemble_vol": "Volatility-sized trend",
    "rotation": "Top-3 rotation",
    "moonshot": "Top-1 moonshot (aggressive)",
    "rsi2_dips": "RSI-2 dip buyer (BTC/ETH)",
    "btc_hold": "Buy & hold BTC",
    "buy_hold": "Equal-weight hold (10 coins)",
}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--start", required=True, help="first day of the paper race, YYYY-MM-DD")
    ap.add_argument("--capital", type=float, default=8000)
    ap.add_argument("--years", type=int, default=5)
    ap.add_argument("--backtest", action="store_true", help="instead of the race: full-history report for every contestant")
    a = ap.parse_args()
    if a.backtest:
        return backtest(a)

    data = {s: fetch_daily(s, a.years) for s in COINS}
    close, opens = panel(data, "close"), panel(data, "open")
    start = pd.Timestamp(a.start, tz="UTC")
    rows = []
    for key, label in CONTESTANTS.items():
        w = contestant(close, key)
        r = run(opens, w, COST)
        live = r[r.index >= start]
        # The last day's open-to-next-open return isn't known yet; drop it.
        live = live.iloc[:-1] if len(live) else live
        eq = float((1 + live).prod()) if len(live) else 1.0
        today = float(live.iloc[-1]) if len(live) else 0.0
        bt = stats(r[(r.index >= close.index[200]) & (r.index < start)])
        rows.append((eq, label, today, bt))

    rows.sort(key=lambda x: -x[0])
    days = max(0, (close.index[-1] - start).days)
    print(f"🏁 Strategy tournament · paper ₹{a.capital:,.0f} each · day {days} (since {a.start}) · "
          f"data to {close.index[-1]:%Y-%m-%d}\n")
    print(f"{'#':<3}{'strategy':<32}{'value':>10}{'total':>9}{'today':>8}   backtest: CAGR / worst drop")
    for i, (eq, label, today, bt) in enumerate(rows, 1):
        print(f"{i:<3}{label:<32}{a.capital * eq:>10,.0f}{(eq - 1) * 100:>+8.2f}%{today * 100:>+7.2f}%   "
              f"{bt.get('CAGR%', '–')}% / {bt.get('maxDD%', '–')}%")
    print("\nA few weeks of results are mostly luck; judge after 2–3 months. Paper only, not financial advice.")


def backtest(a) -> None:
    """Every contestant over the whole history, split into yearly results, after costs."""
    data = {s: fetch_daily(s, a.years) for s in COINS}
    close, opens = panel(data, "close"), panel(data, "open")
    start = close.index[200]                        # after the 200-day averages warm up
    cut = close.index[int(len(close) * 0.6)]
    years = sorted({d.year for d in close.index[close.index >= start]})
    print(f"📈 Backtest of all tournament strategies · {len(COINS)} coins · {start:%Y-%m-%d} → {close.index[-1]:%Y-%m-%d} · "
          f"cost {COST*100:.2f}% per trade · ₹{a.capital:,.0f} start\n")
    print(f"{'strategy':<32}{'final ₹':>11}{'CAGR':>8}{'worst drop':>12}{'sharpe':>8}   recent 40%: CAGR / drop")
    yearly = {}
    for key, label in CONTESTANTS.items():
        r = run(opens, contestant(close, key), COST)
        full, rec = stats(r[r.index >= start]), stats(r[r.index >= cut])
        eq = float((1 + r[r.index >= start]).prod())
        print(f"{label:<32}{a.capital * eq:>11,.0f}{full['CAGR%']:>7}%{full['maxDD%']:>11}%{full['sharpe']:>8}   "
              f"{rec['CAGR%']}% / {rec['maxDD%']}%")
        yearly[label] = {y: (float((1 + r[(r.index >= start) & (r.index.year == y)]).prod()) - 1) * 100 for y in years}
    print("\nYear by year (%):")
    print(f"{'strategy':<32}" + "".join(f"{y:>9}" for y in years))
    for label, ys in yearly.items():
        print(f"{label:<32}" + "".join(f"{ys[y]:>+8.1f}%" for y in years))
    print("\nLook for: positive in most years and a worst drop you could live through. "
          "The first and last years are partial. Past results don't guarantee future ones.")


if __name__ == "__main__":
    main()
