"""Download historical order flow (every trade, with its aggressor side) from Binance's public archive.

CoinDCX's USDT "B-" markets (B-BTC_USDT, B-ETH_USDT, ...) are executed on Binance's order book, and CoinDCX's own
API only returns the last few hundred trades, so Binance's archive is the real order-flow history for those pairs.

Each daily file has: agg_trade_id, price, quantity, first_id, last_id, timestamp, is_buyer_maker, best_match.
is_buyer_maker = True means the SELLER was the aggressor (a market sell hit the bid).

Usage:  python fetch.py BTCUSDT 90          -> data/BTCUSDT/<date>.parquet (or .csv.gz if pyarrow is missing)
"""
import datetime as dt
import io
import ssl
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

import pandas as pd

# python.org's Python on macOS ships without system certificates; certifi's bundle makes https work everywhere.
try:
    import certifi
    SSL = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    SSL = ssl.create_default_context()

BASE = "https://data.binance.vision/data/spot/daily/aggTrades/{s}/{s}-aggTrades-{d}.zip"
COLS = ["agg_id", "price", "qty", "first_id", "last_id", "ts", "is_buyer_maker", "best_match"]


def _save(df: pd.DataFrame, path: Path) -> Path:
    try:
        out = path.with_suffix(".parquet"); df.to_parquet(out, index=False)
    except Exception:
        out = path.with_suffix(".csv.gz"); df.to_csv(out, index=False)
    return out


def fetch_day(symbol: str, day: dt.date, out_dir: Path, retries: int = 4) -> Path | None:
    stem = out_dir / day.isoformat()
    for ext in (".parquet", ".csv.gz"):
        if stem.with_suffix(ext).exists():
            return stem.with_suffix(ext)
    url = BASE.format(s=symbol, d=day.isoformat())
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "orderflow-edge/1.0"})
            with urllib.request.urlopen(req, timeout=180, context=SSL) as r:
                raw = r.read()
            break
        except urllib.error.HTTPError as e:
            if e.code == 404:          # not published yet (today/yesterday) or no such pair
                return None
            last = e
        except Exception as e:
            last = e
        time.sleep(2 ** attempt)
    else:
        hint = ""
        if "CERTIFICATE_VERIFY_FAILED" in str(last):
            hint = ("\n   fix: pip install certifi   (or run 'Install Certificates.command' in /Applications/Python 3.x)")
        print(f"   skipped {day}: {type(last).__name__}: {last}{hint}", flush=True)
        return None
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        with z.open(z.namelist()[0]) as f:
            df = pd.read_csv(f, header=None, names=COLS)
    if not str(df.iloc[0, 0]).lstrip("-").isdigit():   # some files carry a header row
        df = df.iloc[1:]
    df = df.astype({"price": float, "qty": float, "ts": "int64"})
    # Newer archives use microseconds; normalise to milliseconds.
    if df["ts"].iloc[0] > 10**14:
        df["ts"] //= 1000
    df["is_buyer_maker"] = df["is_buyer_maker"].astype(str).str.lower().eq("true")
    return _save(df[["ts", "price", "qty", "is_buyer_maker"]], stem)


def fetch(symbol: str, days: int, root: Path = Path("data")) -> list[Path]:
    out_dir = root / symbol
    out_dir.mkdir(parents=True, exist_ok=True)
    end = dt.date.today() - dt.timedelta(days=1)
    paths, missing = [], []
    for i in range(days, 0, -1):
        day = end - dt.timedelta(days=i - 1)
        p = fetch_day(symbol, day, out_dir)
        print(f"{symbol} {day} {'ok' if p else 'missing'}", flush=True)
        (paths if p else missing).append(p or day)
    print(f"{symbol}: {len(paths)} days downloaded, {len(missing)} missing"
          + (" — run the same command again to retry them (finished days are kept)." if missing else "."))
    return paths


def load(symbol: str, root: Path = Path("data")) -> pd.DataFrame:
    files = sorted((root / symbol).glob("*.parquet")) + sorted((root / symbol).glob("*.csv.gz"))
    if not files:
        raise SystemExit(f"no data for {symbol}: run  python fetch.py {symbol} 90")
    dfs = [pd.read_parquet(f) if f.suffix == ".parquet" else pd.read_csv(f) for f in sorted(files, key=lambda f: f.name)]
    return pd.concat(dfs, ignore_index=True).sort_values("ts", kind="stable")


if __name__ == "__main__":
    fetch(sys.argv[1].upper(), int(sys.argv[2]) if len(sys.argv) > 2 else 90)
