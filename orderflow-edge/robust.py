"""Stress test of the combo strategy: try hard to break it before trusting it with money.

  python robust.py --years 5 > robustness.txt

 1. Settings sensitivity  (moving averages, volatility target, BTC filter length)
 2. Cost stress           (fees 0.10% → 0.50% per trade, and 1% TDS charged on every sell as a worst case)
 3. Late execution        (acting 1–3 days after the signal)
 4. Coin luck             (leave each coin out; 50 random 5-coin portfolios)
 5. Start-date luck       (every 1-year window: how often it loses, worst and best year)
 6. Monte Carlo           (1,000 block-bootstrapped histories: odds of big drawdowns and losing years)
 7. Cheating check        (scramble which coin gets which daily return; a real strategy loses its edge)
"""
import argparse

import numpy as np
import pandas as pd

from portfolio import panel
from signals import COINS
from trend import fetch_daily, stats


def combo_weights(close: pd.DataFrame, mas=(50, 100, 150, 200), vol_target=0.60, btc_ma=100, cap=1.5,
                  use_btc=True, use_vol=True) -> pd.DataFrame:
    n = close.shape[1]
    base = close.notna().astype(float) / n
    above = lambda k: (close > close.rolling(k, min_periods=k).mean()).astype(float)
    ens = sum(above(k) for k in mas) / len(mas)
    w = base * ens
    if use_vol:
        vol = close.pct_change().rolling(60, min_periods=30).std() * np.sqrt(365)
        w = w * (vol_target / vol).clip(upper=cap).fillna(0)
    if use_btc and "BTCUSDT" in close:
        btc_on = above(btc_ma)["BTCUSDT"]
        mask = pd.DataFrame({c: (1.0 if c == "BTCUSDT" else btc_on) for c in close.columns}, index=close.index)
        w = w * mask
    w = w.fillna(0)
    t = w.sum(axis=1)
    return w.div(t.where(t > 1, 1), axis=0)


def returns(opens: pd.DataFrame, w: pd.DataFrame, cost: float, delay: int = 0, tds: float = 0.0) -> pd.Series:
    held = w.shift(1 + delay).fillna(0)
    r = (opens.shift(-1) / opens - 1).fillna(0)
    d = held.diff().fillna(held)
    buys, sells = d.clip(lower=0).sum(axis=1), (-d.clip(upper=0)).sum(axis=1)
    return (held * r).sum(axis=1) - (buys + sells) * cost - sells * tds


def line(label: str, r: pd.Series) -> str:
    s = stats(r)
    return f"  {label:<44}{s['CAGR%']:>8}%{s['maxDD%']:>10}%{s['sharpe']:>8}"


HEAD = f"  {'':<44}{'CAGR':>9}{'worst DD':>11}{'sharpe':>8}"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--years", type=int, default=5)
    ap.add_argument("--cost", type=float, default=0.0015)
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()
    rng = np.random.default_rng(a.seed)

    data = {s: fetch_daily(s, a.years) for s in COINS}
    close, opens = panel(data, "close"), panel(data, "open")
    start = close.index[200]
    live = lambda r: r[r.index >= start]
    base = live(returns(opens, combo_weights(close), a.cost))
    bh = live(returns(opens, pd.DataFrame(1.0, index=close.index, columns=["BTCUSDT"]).reindex(columns=close.columns, fill_value=0.0), a.cost))
    verdicts = []

    print(f"🔬 Combo strategy stress test · {start:%Y-%m-%d} → {close.index[-1]:%Y-%m-%d} · cost {a.cost*100:.2f}% per trade\n")
    print(HEAD)
    print(line("Combo (as designed)", base))
    print(line("Buy & hold BTC (reference)", bh))

    # 1 ── settings
    print("\n1) SETTINGS SENSITIVITY — does it still work if the numbers change?")
    print(HEAD)
    sens = []
    for mas in [(20, 50, 100, 150), (50, 100, 150, 200), (100, 150, 200, 250), (30, 60, 90, 120), (50, 200)]:
        for vt in (0.4, 0.6, 0.8):
            for bm in (50, 100, 200):
                r = live(returns(opens, combo_weights(close, mas, vt, bm), a.cost))
                sens.append((stats(r), mas, vt, bm))
    cagrs = np.array([s[0]["CAGR%"] for s in sens])
    shs = np.array([s[0]["sharpe"] for s in sens])
    print(f"  {len(sens)} combinations: CAGR median {np.median(cagrs):.1f}%, worst {cagrs.min():.1f}%, best {cagrs.max():.1f}%; "
          f"{(cagrs > 0).mean():.0%} profitable; sharpe median {np.median(shs):.2f}")
    print(line("  without the BTC filter", live(returns(opens, combo_weights(close, use_btc=False), a.cost))))
    print(line("  without volatility sizing", live(returns(opens, combo_weights(close, use_vol=False), a.cost))))
    verdicts.append(("Settings", (cagrs > 0).mean() >= 0.8 and np.median(shs) >= 0.5,
                     f"{(cagrs > 0).mean():.0%} of {len(sens)} settings profitable"))

    # 2 ── costs
    print("\n2) COST STRESS — fees per trade (plus TDS worst case)")
    print(HEAD)
    cost_rows = {}
    for c in (0.001, 0.0015, 0.0025, 0.004, 0.005):
        r = live(returns(opens, combo_weights(close), c))
        cost_rows[c] = stats(r)
        print(line(f"fee+slippage {c*100:.2f}%", r))
    r_tds = live(returns(opens, combo_weights(close), a.cost, tds=0.01))
    print(line("0.15% + 1% TDS lost on every sell (worst case)", r_tds))
    verdicts.append(("Costs", cost_rows[0.005]["CAGR%"] > 0, f"CAGR at 0.5% fees: {cost_rows[0.005]['CAGR%']}%"))

    # 3 ── delay
    print("\n3) LATE EXECUTION — acting days after the signal")
    print(HEAD)
    late = {}
    for d in (0, 1, 2, 3):
        r = live(returns(opens, combo_weights(close), a.cost, delay=d))
        late[d] = stats(r)
        print(line(f"{d} day(s) late", r))
    verdicts.append(("Late execution", late[2]["CAGR%"] > 0, f"CAGR when 2 days late: {late[2]['CAGR%']}%"))

    # 4 ── coins
    print("\n4) COIN LUCK — does one coin carry it?")
    print(HEAD)
    loo = []
    for c in close.columns:
        cols = [x for x in close.columns if x != c]
        r = live(returns(opens[cols], combo_weights(close[cols]), a.cost))
        loo.append(stats(r)["CAGR%"])
        print(line(f"without {c[:-4]}", r))
    subs = []
    alts = [c for c in close.columns if c != "BTCUSDT"]
    for _ in range(50):
        cols = ["BTCUSDT"] + list(rng.choice(alts, 4, replace=False))
        subs.append(stats(live(returns(opens[cols], combo_weights(close[cols]), a.cost)))["CAGR%"])
    subs = np.array(subs)
    print(f"  50 random 5-coin portfolios (BTC + 4): CAGR median {np.median(subs):.1f}%, worst {subs.min():.1f}%, "
          f"{(subs > 0).mean():.0%} profitable")
    verdicts.append(("Coin luck", min(loo) > 0 and (subs > 0).mean() >= 0.8,
                     f"worst leave-one-out CAGR {min(loo):.1f}%, {(subs > 0).mean():.0%} of random portfolios profitable"))

    # 5 ── start dates
    print("\n5) START-DATE LUCK — every 1-year window (starting each week)")
    eq, eqb = (1 + base).cumprod(), (1 + bh).cumprod()
    yr = [(eq.iloc[i + 365] / eq.iloc[i] - 1) * 100 for i in range(0, len(eq) - 365, 7)]
    yrb = [(eqb.iloc[i + 365] / eqb.iloc[i] - 1) * 100 for i in range(0, len(eqb) - 365, 7)]
    yr, yrb = np.array(yr), np.array(yrb)
    print(f"  {len(yr)} windows · combo: {np.mean(yr > 0):.0%} positive, median {np.median(yr):+.1f}%, "
          f"worst {yr.min():+.1f}%, best {yr.max():+.1f}%")
    print(f"  {'':<15}BTC hold: {np.mean(yrb > 0):.0%} positive, median {np.median(yrb):+.1f}%, worst {yrb.min():+.1f}%, best {yrb.max():+.1f}%")
    verdicts.append(("Start date", np.mean(yr > 0) >= 0.6 and yr.min() > -40,
                     f"{np.mean(yr > 0):.0%} of 1-year windows positive, worst {yr.min():+.1f}%"))

    # 6 ── Monte Carlo
    print("\n6) MONTE CARLO — 1,000 reshuffled histories (30-day blocks of real days)")
    rv = base.to_numpy()
    n, blk = len(rv), 30
    cag, dds, y1 = [], [], []
    for _ in range(1000):
        idx = np.concatenate([np.arange(s, s + blk) for s in rng.integers(0, n - blk, n // blk + 1)])[:n]
        sim = rv[idx]
        e = np.cumprod(1 + sim)
        cag.append((e[-1] ** (365 / n) - 1) * 100)
        dds.append(((e / np.maximum.accumulate(e)) - 1).min() * 100)
        y1.append((np.prod(1 + sim[:365]) - 1) * 100)
    cag, dds, y1 = map(np.array, (cag, dds, y1))
    print(f"  CAGR: 5th pct {np.percentile(cag, 5):.1f}% · median {np.median(cag):.1f}% · 95th pct {np.percentile(cag, 95):.1f}%")
    print(f"  Worst drawdown: median {np.median(dds):.1f}% · 1-in-20 bad case {np.percentile(dds, 5):.1f}%")
    print(f"  Chance a given year loses money: {np.mean(y1 < 0):.0%} · loses more than 20%: {np.mean(y1 < -20):.0%}")
    verdicts.append(("Monte Carlo", np.percentile(cag, 5) > 0, f"5th-percentile CAGR {np.percentile(cag, 5):.1f}%"))

    # 7 ── cheating check
    print("\n7) CHEATING CHECK — scramble which coin gets which day's return (signals should stop working)")
    fake = []
    rets = (close.pct_change()).to_numpy()
    for _ in range(20):
        perm = rets.copy()
        for t in range(perm.shape[0]):
            ok = ~np.isnan(perm[t])
            perm[t, ok] = rng.permutation(perm[t, ok])
        c2 = pd.DataFrame(np.nancumprod(np.nan_to_num(perm, nan=0.0) + 1, axis=0), index=close.index, columns=close.columns)
        c2 = c2.where(close.notna())
        o2 = c2.shift(1).fillna(c2)
        fake.append(stats(live(returns(o2, combo_weights(c2), a.cost)))["sharpe"])
    fake = np.array(fake)
    real_sh = stats(base)["sharpe"]
    print(f"  real sharpe {real_sh:.2f} vs scrambled: median {np.median(fake):.2f}, best {fake.max():.2f}")
    print("  (scrambling keeps the market's overall ups and downs, so some edge from riding the whole market remains;")
    print("   the gap between real and scrambled is what the coin-by-coin signals add)")
    verdicts.append(("Cheating check", real_sh > fake.max(), f"real {real_sh:.2f} vs scrambled best {fake.max():.2f}"))

    print("\n═══ VERDICT ═══")
    for name, ok, why in verdicts:
        print(f"  {'PASS' if ok else 'FAIL'}  {name:<16} {why}")
    passed = sum(ok for _, ok, _ in verdicts)
    print(f"\n  {passed}/{len(verdicts)} tests passed. " + ("Robust enough to paper-trade, then use small real money." if passed >= 6
          else "Fragile: don't put real money in until the failures are understood."))


if __name__ == "__main__":
    main()
