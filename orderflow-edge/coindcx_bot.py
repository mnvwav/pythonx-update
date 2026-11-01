"""Automated CoinDCX trading of the combo strategy, once a day.

PRETEND MODE BY DEFAULT: it only prints the orders it would place. Real orders go out only when the environment
variable LIVE_TRADING is exactly "yes" (a GitHub repository *variable* you set yourself) AND API keys are present.

Secrets (GitHub → Settings → Secrets → Actions), from a CoinDCX API key with WITHDRAWALS DISABLED:
  COINDCX_KEY, COINDCX_SECRET
Settings (GitHub → Settings → Variables → Actions), all optional:
  LIVE_TRADING   "yes" to place real orders (anything else = pretend)
  BOT_CAPITAL    most INR the bot may manage (default 8000)
  BOT_MAX_DD     stop buying if the managed value falls this far below its peak, e.g. 0.30 (default)

Each run: compute today's combo targets (from public daily candles) → read CoinDCX balances and INR prices →
sell what's above target, then buy what's below, skipping changes smaller than 5% of capital or below the
market's minimum order → write bot_state.json (peak value, history) and bot_log.txt for the email.
"""
import hashlib
import hmac
import json
import math
import os
import ssl
import time
import urllib.request
from pathlib import Path

from portfolio import panel, weights
from signals import COINS
from trend import fetch_daily

try:
    import certifi
    SSL = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    SSL = ssl.create_default_context()

BASE = "https://api.coindcx.com"
STATE = Path("bot_state.json")


class CoinDCX:
    def __init__(self, key: str, secret: str):
        self.key, self.secret = key, secret.encode()

    def _req(self, path: str, body: dict | None = None):
        if body is None:
            req = urllib.request.Request(BASE + path, headers={"User-Agent": "x11-trend-bot/1.0"})
        else:
            body = {**body, "timestamp": int(time.time() * 1000)}
            payload = json.dumps(body, separators=(",", ":"))
            sig = hmac.new(self.secret, payload.encode(), hashlib.sha256).hexdigest()
            req = urllib.request.Request(BASE + path, data=payload.encode(), method="POST", headers={
                "Content-Type": "application/json", "X-AUTH-APIKEY": self.key, "X-AUTH-SIGNATURE": sig,
                "User-Agent": "x11-trend-bot/1.0"})
        with urllib.request.urlopen(req, timeout=20, context=SSL) as r:
            return json.loads(r.read())

    def tickers(self) -> dict[str, float]:
        return {t["market"]: float(t["last_price"]) for t in self._req("/exchange/ticker") if t.get("last_price")}

    def markets(self) -> dict[str, dict]:
        return {m["coindcx_name"]: m for m in self._req("/exchange/v1/markets_details")}

    def balances(self) -> dict[str, float]:
        return {b["currency"]: float(b["balance"]) for b in self._req("/exchange/v1/users/balances", {})}

    def market_order(self, market: str, side: str, qty: float):
        return self._req("/exchange/v1/orders/create", {"side": side, "order_type": "market_order",
                                                         "market": market, "total_quantity": qty})


def round_down(x: float, decimals: int) -> float:
    f = 10 ** decimals
    return math.floor(x * f) / f


def plan(targets: dict[str, float], holdings: dict[str, float], prices: dict[str, float], cash: float,
         capital: float, info: dict[str, dict], min_change: float, halt_buys: bool) -> tuple[list, float]:
    """Orders (market, side, qty, inr) to move the managed portfolio to the targets. Sells first, then buys."""
    value = cash + sum(holdings.get(c, 0) * prices[c] for c in targets if c in prices)
    managed = min(value, capital)
    orders = []
    for coin, w in targets.items():
        if coin not in prices:
            continue
        market = f"{coin}INR"
        cur = holdings.get(coin, 0) * prices[coin]
        diff = w * managed - cur
        m = info.get(market, {})
        min_notional = float(m.get("min_notional") or 100)
        if abs(diff) < max(min_change * capital, min_notional):
            # Still sell a leftover position entirely when the target is zero.
            if not (w == 0 and cur >= min_notional):
                continue
            diff = -cur
        if diff > 0 and halt_buys:
            continue
        qty = round_down(abs(diff) / prices[coin], int(m.get("target_currency_precision", 6)))
        if qty <= 0 or qty < float(m.get("min_quantity") or 0):
            continue
        orders.append((market, "buy" if diff > 0 else "sell", qty, round(qty * prices[coin], 2)))
    orders.sort(key=lambda o: o[1] != "sell")          # sells first, to free INR for buys
    return orders, value


def main() -> None:
    live = os.environ.get("LIVE_TRADING", "").strip().lower() == "yes"
    capital = float(os.environ.get("BOT_CAPITAL") or 8000)
    max_dd = float(os.environ.get("BOT_MAX_DD") or 0.30)
    key, secret = os.environ.get("COINDCX_KEY", ""), os.environ.get("COINDCX_SECRET", "")
    out = []
    say = lambda s="": (print(s), out.append(s))

    data = {s: fetch_daily(s, 2) for s in COINS}
    close = panel(data, "close")
    w = weights(close, "combo", "BTCUSDT").iloc[-1]
    targets = {s[:-4]: float(w[s]) for s in COINS}
    day = close.index[-1]

    say(f"🤖 CoinDCX bot · {'LIVE' if live else 'PRETEND (no real orders)'} · signals from {day:%Y-%m-%d} · "
        f"capital limit ₹{capital:,.0f}")
    if not key or not secret:
        say("No CoinDCX API keys set: showing targets only.")
        for c, t in targets.items():
            say(f"  {c:<6}{t:>7.1%}  ≈ ₹{t * capital:,.0f}")
        Path("bot_log.txt").write_text("\n".join(out) + "\n")
        return

    api = CoinDCX(key, secret)
    tick, info = api.tickers(), api.markets()
    prices = {c: tick[f"{c}INR"] for c in targets if f"{c}INR" in tick}
    missing = [c for c in targets if c not in prices]
    if missing:
        say(f"No INR market on CoinDCX for: {', '.join(missing)} (their share stays in cash)")
    bal = api.balances()
    cash = bal.get("INR", 0.0)
    holdings = {c: bal.get(c, 0.0) for c in targets}

    state = json.loads(STATE.read_text()) if STATE.exists() else {"peak": 0, "history": []}
    value_now = cash + sum(holdings[c] * prices[c] for c in prices)
    managed = min(value_now, capital)
    state["peak"] = max(state.get("peak", 0), managed)
    halt = managed < state["peak"] * (1 - max_dd)
    if halt:
        say(f"⚠️ Safety stop: managed value ₹{managed:,.0f} is more than {max_dd:.0%} below its peak "
            f"₹{state['peak']:,.0f}. No new buys until you review it (sells still allowed).")

    orders, value = plan(targets, holdings, prices, cash, capital, info, 0.05, halt)
    say(f"Account value ₹{value:,.0f} (INR cash ₹{cash:,.0f}); managing ₹{managed:,.0f}")
    if not orders:
        say("No trades needed today.")
    for market, side, qty, inr in orders:
        if live:
            try:
                r = api.market_order(market, side, qty)
                say(f"  ✅ {side.upper():<4} {qty} {market} (≈ ₹{inr:,.0f}) · order {r.get('orders', [{}])[0].get('id', '?')}")
            except Exception as e:
                say(f"  ❌ {side.upper():<4} {qty} {market} failed: {e}")
        else:
            say(f"  (pretend) {side.upper():<4} {qty} {market} ≈ ₹{inr:,.0f}")
    state["history"] = (state.get("history", []) + [{"date": f"{day:%Y-%m-%d}", "value": round(value, 2),
                                                    "managed": round(managed, 2), "live": live,
                                                    "orders": len(orders)}])[-400:]
    STATE.write_text(json.dumps(state, indent=1))
    first = state["history"][0]["managed"] if state["history"] else managed
    say(f"Since the bot started: ₹{first:,.0f} → ₹{managed:,.0f} ({(managed / first - 1) * 100 if first else 0:+.2f}%), "
        "not counting deposits.")
    Path("bot_log.txt").write_text("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
