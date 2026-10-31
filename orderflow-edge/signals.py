"""Today's target portfolio for the "combo" trend strategy, plus a paper-trading log.

  python signals.py 100000            # capital in INR (or USDT): prints what to hold today
  python signals.py 100000 --paper    # also records today in paper_log.csv and shows paper performance

Run once a day after the daily close (00:00 UTC = 5:30 am India). Rebalance only when a coin's target moves by
more than --min-change of the account, to avoid paying fees for tiny adjustments.
"""
import argparse
import datetime as dt
from pathlib import Path

import pandas as pd

from portfolio import panel, weights
from trend import fetch_daily

COINS = ["BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "DOGEUSDT", "ADAUSDT", "LINKUSDT", "LTCUSDT", "TRXUSDT"]
LOG = Path("paper_log.csv")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("capital", type=float)
    ap.add_argument("--coins", nargs="+", default=COINS)
    ap.add_argument("--min-change", type=float, default=0.02, help="ignore target changes smaller than 2%% of the account")
    ap.add_argument("--paper", action="store_true")
    a = ap.parse_args()

    # Refresh daily candles (the last ~400 days are enough for the 200-day averages and 60-day volatility).
    data = {s: fetch_daily(s, 2) for s in a.coins}
    close = panel(data, "close")
    w = weights(close, "combo", "BTCUSDT")
    day = close.index[-1]
    age = (pd.Timestamp.now(tz="UTC").normalize() - day).days
    if age > 2:
        raise SystemExit(f"Latest daily candle is {day:%Y-%m-%d}, {age} days old: the data source is behind. "
                         "Not logging stale signals; try again later.")
    today, prev = w.iloc[-1], w.iloc[-2]
    btc_up = close["BTCUSDT"].iloc[-1] > close["BTCUSDT"].rolling(100).mean().iloc[-1]

    print(f"\nCombo strategy · signals from the {day:%Y-%m-%d} daily close · capital {a.capital:,.0f}")
    print(f"BTC trend filter: {'ON — altcoins allowed' if btc_up else 'OFF — altcoins go to cash'}\n")
    print(f"{'coin':<10}{'target':>8}{'amount':>12}   action")
    for s in a.coins:
        t, p = today[s], prev[s]
        change = t - p
        act = "—"
        if abs(change) >= a.min_change:
            act = f"{'BUY' if change > 0 else 'SELL'} {abs(change) * a.capital:,.0f}"
        elif t == 0 and p > 0:
            act = f"SELL all ({p * a.capital:,.0f})"
        print(f"{s[:-4]:<10}{t:>8.1%}{t * a.capital:>12,.0f}   {act}")
    cash = 1 - today.sum()
    print(f"{'CASH':<10}{cash:>8.1%}{cash * a.capital:>12,.0f}\n")

    if a.paper:
        opens = panel(data, "open")
        r = (opens.shift(-1) / opens - 1).fillna(0)
        row = {"date": f"{day:%Y-%m-%d}", **{s: round(today[s], 4) for s in a.coins}}
        log = pd.read_csv(LOG) if LOG.exists() else pd.DataFrame()
        if log.empty or log["date"].iloc[-1] != row["date"]:
            log = pd.concat([log, pd.DataFrame([row])], ignore_index=True)
            log.to_csv(LOG, index=False)
        # Paper equity: each logged day's weights earn the next day's open-to-open move, minus 0.15% on changes.
        eq, prev_w = 1.0, None
        for _, rw in log.iterrows():
            d = pd.Timestamp(rw["date"], tz="UTC")
            wv = pd.Series({s: rw.get(s, 0) for s in a.coins}).fillna(0)
            nxt = r.index[r.index > d]
            if len(nxt) == 0:
                break
            day_ret = float((wv * r.loc[nxt[0], a.coins]).sum())
            turn = float((wv - (prev_w if prev_w is not None else 0)).abs().sum())
            eq *= 1 + day_ret - 0.0015 * turn
            prev_w = wv
        print(f"Paper trading: {len(log)} days logged since {log['date'].iloc[0]}, "
              f"value {a.capital * eq:,.0f} ({(eq - 1) * 100:+.2f}%)")


if __name__ == "__main__":
    main()
