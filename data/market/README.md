# Synthetic market data

AAPL.csv and MSFT.csv contain fabricated prices and volumes, not historical observations. Symbols exercise provider routing only. Do not use these files for trading, performance claims or live valuations.

Use one file per symbol with columns `timestamp,open,high,low,close,volume`. Timestamps accept ISO-8601 or YYYY-MM-DD; volume is optional. Supply licensed data privately for deployments and verify redistribution terms before adding datasets to source control.
