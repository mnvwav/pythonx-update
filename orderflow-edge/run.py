"""Find out whether order flow gives an edge, with costs and out-of-sample testing.

  python fetch.py BTCUSDT 90          # once per symbol (Binance public archive; no keys)
  python run.py BTCUSDT ETHUSDT SOLUSDT --fee 0.001 --long-only

Writes report.md: per symbol and strategy, the chosen settings, in-sample stats and the out-of-sample stats that
actually matter. An edge counts only if it holds out-of-sample with enough trades and a profit factor above ~1.2.
"""
import argparse
from pathlib import Path

import pandas as pd

from backtest import STRATEGIES, Costs, walk_forward
from features import add_features, bars
from fetch import load
from momentum import MOMENTUM, five_min, walk_forward_m


def verdict(o: dict, min_trades: int = 30) -> str:
    if o.get("trades", 0) < min_trades:
        return "too few trades to judge"
    small = " (small sample)" if o["trades"] < 30 else ""
    if o["expectancy%"] > 0 and o["profit factor"] >= 1.2:
        return "EDGE (holds out-of-sample)" + small
    if o["expectancy%"] > 0:
        return "weak: positive but thin after costs" + small
    return "no edge after costs"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("symbols", nargs="+")
    ap.add_argument("--fee", type=float, default=0.001, help="fee per side, e.g. 0.001 = 0.1%%")
    ap.add_argument("--slippage", type=float, default=0.0002)
    ap.add_argument("--long-only", action="store_true", help="spot accounts can't short")
    ap.add_argument("--out", default="report.md")
    ap.add_argument("--style", choices=["orderflow", "momentum"], default="orderflow",
                    help="momentum = opening-range breakout + VWAP pullback (5-min bars, session-based)")
    a = ap.parse_args()
    costs = Costs(fee=a.fee, slippage=a.slippage)

    lines = ["# Order-flow edge report", "",
             f"Costs: {a.fee*100:.3f}% fee + {a.slippage*100:.3f}% slippage per side. "
             f"{'Long only.' if a.long_only else 'Long and short.'} "
             "Settings chosen on the first 2/3 of the data; judged on the last 1/3 only.", ""]
    for sym in a.symbols:
        print(f"== {sym}: building bars", flush=True)
        b1 = bars(load(sym.upper()))
        if a.style == "momentum":
            b5 = five_min(b1)
            lines += [f"## {sym} — momentum (ORB / VWAP pullback)", "", f"{b5.index[0]:%Y-%m-%d} → {b5.index[-1]:%Y-%m-%d}", "",
                      "| strategy | settings | OOS trades | win% | expectancy% | profit factor | total% | max DD% | verdict |",
                      "|---|---|---|---|---|---|---|---|---|"]
            for name in MOMENTUM:
                print(f"   {name}", flush=True)
                w = walk_forward_m(b5, name, costs)
                if "note" in w:
                    lines.append(f"| {name} | – | – | – | – | – | – | – | {w['note']} |"); continue
                o = w["out_of_sample"].stats(); p = w["params"]
                lines.append(f"| {name} | open {p.open_utc} UTC, range {p.orb_min}m, rvol≥{p.rvol}, {p.r_mult}R, stop {p.stop} | "
                             f"{o.get('trades', 0)} | {o.get('win%', '–')} | {o.get('expectancy%', '–')} | {o.get('profit factor', '–')} | "
                             f"{o.get('total%', '–')} | {o.get('max DD%', '–')} | {verdict(o, 15)} |")
                w["out_of_sample"].trades.to_csv(f"trades_{sym}_{name}.csv", index=False)
            lines.append("")
            continue
        f = add_features(b1)
        lines += [f"## {sym}", "", f"{f.index[0]:%Y-%m-%d} → {f.index[-1]:%Y-%m-%d}, {len(f):,} one-minute bars", "",
                  "| strategy | settings | OOS trades | win% | expectancy% | profit factor | total% | max DD% | verdict |",
                  "|---|---|---|---|---|---|---|---|---|"]
        for name in STRATEGIES:
            print(f"   {name}", flush=True)
            w = walk_forward(f, name, costs, long_only=a.long_only)
            if "note" in w:
                lines.append(f"| {name} | – | – | – | – | – | – | – | {w['note']} |")
                continue
            o = w["out_of_sample"].stats()
            p = w["params"]
            lines.append(f"| {name} | k={p.k} stop={p.stop_atr}ATR tgt={p.target_atr}ATR max={p.max_bars}m | "
                         f"{o.get('trades', 0)} | {o.get('win%', '–')} | {o.get('expectancy%', '–')} | "
                         f"{o.get('profit factor', '–')} | {o.get('total%', '–')} | {o.get('max DD%', '–')} | {verdict(o)} |")
            w["out_of_sample"].trades.to_csv(f"trades_{sym}_{name}.csv", index=False)
        lines.append("")
    lines += ["## Read this before trading", "",
              "- Out-of-sample is one period; markets change. Paper-trade or use tiny size for weeks first.",
              "- India: 1% TDS on crypto sells and 30% tax on gains (losses can't offset gains) — "
              "high-frequency strategies lose a lot of working capital to TDS. Factor that in.",
              "- Signals come from Binance flow; CoinDCX B- pairs execute there, but INR pairs can lag or gap."]
    Path(a.out).write_text("\n".join(lines))
    print(f"wrote {a.out}")


if __name__ == "__main__":
    main()
