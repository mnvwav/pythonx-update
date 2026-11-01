"""Price-error scanner for Binance (measurement only: no keys, no orders).

Every second it reads the best bid/ask of every Binance spot pair (one request) and checks every triangle
START → X → Y → START (e.g. USDT → BTC → ETH → USDT) for a loop that ends with more than it started, after the
fee on all three trades. That is the "price error" a fast bot could actually execute on one exchange.

For each opportunity it records the net edge, how much money could have gone through at those prices
(limited by the smallest top-of-book quantity) and how many consecutive seconds it lasted.

  python scanner.py --minutes 60 --fee 0.001        # fee per trade (0.00075 if you pay fees in BNB)
  python scanner.py --minutes 1440 --start USDT FDUSD

Writes opportunities.csv and prints a summary at the end. It needs to run where Binance's API is reachable
(your Mac); it can't run inside the chat environment.
"""
import argparse
import csv
import json
import ssl
import time
import urllib.request
from collections import defaultdict
from itertools import permutations

try:
    import certifi
    SSL = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    SSL = ssl.create_default_context()

API = "https://api.binance.com/api/v3"


def get(path: str):
    req = urllib.request.Request(API + path, headers={"User-Agent": "price-error-scanner/1.0"})
    with urllib.request.urlopen(req, timeout=10, context=SSL) as r:
        return json.loads(r.read())


def markets() -> dict[str, tuple[str, str]]:
    """symbol -> (base, quote) for every spot pair that is trading."""
    info = get("/exchangeInfo?permissions=SPOT")
    return {s["symbol"]: (s["baseAsset"], s["quoteAsset"]) for s in info["symbols"] if s["status"] == "TRADING"}


def build_triangles(pairs: dict[str, tuple[str, str]], starts: list[str]) -> list[tuple]:
    """Every loop start -> a -> b -> start that exists as three tradable pairs."""
    edge = {}
    for sym, (b, q) in pairs.items():
        edge[(q, b)] = (sym, "buy")    # spend quote, get base: buy at the ask
        edge[(b, q)] = (sym, "sell")   # spend base, get quote: sell at the bid
    by_from = defaultdict(list)
    for (f, t) in edge:
        by_from[f].append(t)
    tri = []
    for s in starts:
        for a in by_from[s]:
            for b in by_from[a]:
                if b != s and (b, s) in edge:
                    tri.append((s, a, b, edge[(s, a)], edge[(a, b)], edge[(b, s)]))
    return tri


def walk(tri, book, fee):
    """Return (net multiplier, max size in start currency) for one loop, or None if a price is missing."""
    # amount = units of the current asset held per 1 unit of the start currency put in.
    # A leg can absorb at most `room` units of the current asset at the quoted price; in start currency that is room / amount.
    amount, cap = 1.0, float("inf")
    for sym, side in tri[3:]:
        q = book.get(sym)
        if not q:
            return None
        bid, bq, ask, aq = q
        if bid <= 0 or ask <= 0:
            return None
        if side == "buy":                     # spend quote at the ask; the book offers aq base = aq*ask quote
            cap = min(cap, aq * ask / amount)
            amount = amount / ask * (1 - fee)
        else:                                 # sell base at the bid; the book takes bq base
            cap = min(cap, bq / amount)
            amount = amount * bid * (1 - fee)
    return amount, cap


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--minutes", type=float, default=60)
    ap.add_argument("--fee", type=float, default=0.001, help="per trade; 0.00075 with BNB fee discount")
    ap.add_argument("--start", nargs="+", default=["USDT"], help="currencies to start/end loops in")
    ap.add_argument("--min-edge", type=float, default=0.0, help="log loops whose net gain exceeds this (0.001 = 0.1%)")
    ap.add_argument("--out", default="opportunities.csv")
    a = ap.parse_args()

    pairs = markets()
    tris = build_triangles(pairs, a.start)
    print(f"{len(pairs)} pairs, {len(tris)} triangles from {', '.join(a.start)}; fee {a.fee*100:.3f}% per trade; "
          f"scanning for {a.minutes:g} min…", flush=True)

    end = time.time() + a.minutes * 60
    scans, errors = 0, 0
    live = {}                                  # loop -> (first seen, best edge, best size)
    finished = []                              # (loop, seconds, best edge, best size)
    best_gross = -1.0
    with open(a.out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["time", "loop", "net_edge_pct", "max_size_start_ccy"])
        while time.time() < end:
            t0 = time.time()
            try:
                book = {d["symbol"]: (float(d["bidPrice"]), float(d["bidQty"]), float(d["askPrice"]), float(d["askQty"]))
                        for d in get("/ticker/bookTicker")}
            except Exception:
                errors += 1
                time.sleep(2)
                continue
            scans += 1
            seen = set()
            for tri in tris:
                r = walk(tri, book, a.fee)
                if not r:
                    continue
                mult, cap = r
                best_gross = max(best_gross, mult)
                if mult - 1 > a.min_edge:
                    key = "→".join(tri[:3]) + "→" + tri[0]
                    seen.add(key)
                    w.writerow([f"{t0:.0f}", key, round((mult - 1) * 100, 4), round(cap, 2)])
                    first, e, c = live.get(key, (t0, 0, 0))
                    live[key] = (first, max(e, mult - 1), max(c, cap))
            for key in list(live):
                if key not in seen:
                    first, e, c = live.pop(key)
                    finished.append((key, time.time() - first, e, c))
            if scans % 60 == 0:
                f.flush()
                print(f"  {scans} scans · {len(finished) + len(live)} opportunities so far · "
                      f"best loop ever {(best_gross - 1) * 100:+.3f}% after fees", flush=True)
            time.sleep(max(0.0, 1.0 - (time.time() - t0)))   # once per second (Binance weight limits)

    finished += [(k, time.time() - v[0], v[1], v[2]) for k, v in live.items()]
    print(f"\n=== {scans} scans in {a.minutes:g} min ({errors} failed requests) ===")
    if not finished:
        print(f"No loop beat {a.fee*300:.2f}% of fees even once. Best loop: {(best_gross - 1) * 100:+.3f}% after fees.")
        print("Verdict: no price errors a retail bot could catch at this fee level.")
        return
    tradable = [x for x in finished if x[3] >= 10]
    print(f"{len(finished)} opportunities; {len(tradable)} with at least 10 {a.start[0]} of depth at the quoted prices.")
    print(f"Median length {sorted(x[1] for x in finished)[len(finished)//2]:.1f} s; "
          f"median edge {sorted(x[2] for x in finished)[len(finished)//2]*100:.3f}%")
    print("Top 10 by edge × size:")
    for k, secs, e, c in sorted(finished, key=lambda x: -x[2] * min(x[3], 1e6))[:10]:
        print(f"  {k:<30} {e*100:+.3f}%  up to {c:,.0f} {a.start[0]}  lasted {secs:.0f}s  → ~{e*c:,.2f} profit")
    print("\nReality check: you'd need to place 3 orders in well under a second, and pros see the same gaps first."
          " Gaps lasting under 2 s are effectively unreachable from India.")


if __name__ == "__main__":
    main()
