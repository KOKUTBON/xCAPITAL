ALTER TABLE portfolio_funding_source
  ADD COLUMN IF NOT EXISTS provider_reference text;
