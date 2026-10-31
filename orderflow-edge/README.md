# Order-flow edge finder (CoinDCX / Binance)

Tests whether **order flow** (who is aggressively buying vs selling) predicts short-term moves well enough to
beat trading costs, using 90 days of every trade.

## Run
```
pip install pandas numpy pyarrow
python fetch.py BTCUSDT 90       # repeat for ETHUSDT, SOLUSDT, ... (public data, no keys)
python run.py BTCUSDT ETHUSDT SOLUSDT --long-only --fee 0.001
```
Read `report.md`. Trades for each strategy are in `trades_<SYMBOL>_<strategy>.csv`.

## Data
Binance's public archive (`data.binance.vision`): every trade with its aggressor side. CoinDCX's USDT "B-" pairs
execute on Binance's book, and CoinDCX's API only returns the last few hundred trades.

## Signals (1-minute bars)
| strategy | idea |
|---|---|
| delta_breakout | strong aggressive buying (5-min delta z-score > k) while price breaks its 30-min high |
| absorption | heavy aggressive selling but price doesn't fall: sellers absorbed by big bids → long (and mirror) |
| cvd_divergence | price makes a 60-min low but cumulative delta doesn't: the drop has no real selling behind it |
| whale_flow | the largest 1% of trades net-buying over 15 min (z-score > k) |

## Honesty rules
- Signals use only past data; entries fill at the next bar's open; stop beats target when both hit in a bar.
- Fee + slippage on both sides of every trade.
- Settings picked on the first 2/3 of the period, judged only on the last 1/3.
- Sanity-checked on synthetic random-walk data: every strategy correctly shows no edge.

Not financial advice. India: 1% TDS on sells, 30% tax on gains with no loss offset.
