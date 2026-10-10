-- Banking accounts + instant transfers
CREATE TABLE IF NOT EXISTS portfolio_banking_account (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  cash double precision NOT NULL,
  currency varchar(8) NOT NULL,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_banking_account_user ON portfolio_banking_account(user_id);

CREATE TABLE IF NOT EXISTS portfolio_banking_transfer (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  direction varchar(32) NOT NULL,
  amount double precision NOT NULL,
  currency varchar(8) NOT NULL,
  status varchar(32) NOT NULL,
  note text,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_banking_transfer_user ON portfolio_banking_transfer(user_id);

-- Proprietary research coverage (mocked)
CREATE TABLE IF NOT EXISTS portfolio_research_coverage (
  id varchar(64) PRIMARY KEY,
  symbol varchar(32) NOT NULL,
  rating varchar(32) NOT NULL,
  price_target double precision,
  focus_list boolean NOT NULL DEFAULT false,
  analyst varchar(128),
  summary text,
  source varchar(64) NOT NULL,
  published_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_research_coverage_symbol ON portfolio_research_coverage(symbol);
CREATE INDEX IF NOT EXISTS idx_research_coverage_focus ON portfolio_research_coverage(focus_list);

-- Screener universe (mocked fundamentals)
CREATE TABLE IF NOT EXISTS portfolio_screener_security (
  symbol varchar(32) PRIMARY KEY,
  name varchar(128) NOT NULL,
  sector varchar(64),
  industry varchar(64),
  market_cap double precision,
  pe_ratio double precision,
  dividend_yield double precision,
  asset_class varchar(32),
  instrument_type varchar(32),
  currency varchar(8),
  rating varchar(32),
  price_target double precision,
  focus_list boolean NOT NULL DEFAULT false
);

-- Wealth planning
CREATE TABLE IF NOT EXISTS portfolio_wealth_plan (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  plan_type varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  starting_balance double precision NOT NULL,
  target_balance double precision NOT NULL,
  monthly_contribution double precision NOT NULL,
  horizon_years integer NOT NULL,
  expected_return double precision,
  volatility double precision,
  simulation_count integer,
  success_probability double precision,
  median_outcome double precision,
  p10_outcome double precision,
  p90_outcome double precision,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL,
  last_simulated_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_wealth_plan_user ON portfolio_wealth_plan(user_id);

-- Auto-invest fee tracking + plan enhancements
ALTER TABLE IF EXISTS portfolio_auto_invest_plan
  ADD COLUMN IF NOT EXISTS goal_type varchar(32),
  ADD COLUMN IF NOT EXISTS advisory_fee_bps_annual double precision DEFAULT 35,
  ADD COLUMN IF NOT EXISTS minimum_balance double precision DEFAULT 500,
  ADD COLUMN IF NOT EXISTS last_fee_charged_at timestamp with time zone;

UPDATE portfolio_auto_invest_plan
  SET goal_type = COALESCE(goal_type, 'GENERAL_INVESTING'),
      advisory_fee_bps_annual = COALESCE(advisory_fee_bps_annual, 35),
      minimum_balance = COALESCE(minimum_balance, 500)
  WHERE goal_type IS NULL OR advisory_fee_bps_annual IS NULL OR minimum_balance IS NULL;

CREATE TABLE IF NOT EXISTS portfolio_auto_invest_fee (
  id varchar(64) PRIMARY KEY,
  plan_id varchar(64) NOT NULL,
  user_id varchar(64) NOT NULL,
  amount double precision NOT NULL,
  equity double precision NOT NULL,
  fee_bps_annual double precision NOT NULL,
  charge_days integer NOT NULL,
  status varchar(32) NOT NULL,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auto_invest_fee_plan ON portfolio_auto_invest_fee(plan_id);

-- Rewards/bonuses (mock offers)
CREATE TABLE IF NOT EXISTS portfolio_reward_offer (
  id varchar(64) PRIMARY KEY,
  name varchar(128) NOT NULL,
  description text,
  min_deposit double precision NOT NULL,
  bonus_amount double precision NOT NULL,
  currency varchar(8) NOT NULL,
  status varchar(32) NOT NULL,
  created_at timestamp with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolio_reward_enrollment (
  id varchar(64) PRIMARY KEY,
  offer_id varchar(64) NOT NULL,
  user_id varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  qualified_at timestamp with time zone,
  paid_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reward_enrollment_user ON portfolio_reward_enrollment(user_id);

-- Seed data (mock proprietary research + screener universe + rewards)
INSERT INTO portfolio_research_coverage (id, symbol, rating, price_target, focus_list, analyst, summary, source, published_at, created_at) VALUES
  ('rcov-aapl', 'AAPL', 'OVERWEIGHT', 215, true, 'JPM Research', 'Premium hardware/services mix supports resilient margins.', 'JPM_RESEARCH', NOW() - INTERVAL '10 days', NOW()),
  ('rcov-msft', 'MSFT', 'OVERWEIGHT', 420, true, 'JPM Research', 'Cloud momentum and AI attach rate remain strong.', 'JPM_RESEARCH', NOW() - INTERVAL '12 days', NOW()),
  ('rcov-jpm', 'JPM', 'NEUTRAL', 175, false, 'JPM Research', 'Rate sensitivity normalizes as credit costs normalize.', 'JPM_RESEARCH', NOW() - INTERVAL '14 days', NOW()),
  ('rcov-xom', 'XOM', 'UNDERWEIGHT', 95, false, 'JPM Research', 'Upstream margins face mean reversion in 2025.', 'JPM_RESEARCH', NOW() - INTERVAL '18 days', NOW());

INSERT INTO portfolio_screener_security (symbol, name, sector, industry, market_cap, pe_ratio, dividend_yield, asset_class, instrument_type, currency, rating, price_target, focus_list) VALUES
  ('AAPL', 'Apple Inc.', 'Technology', 'Consumer Electronics', 2600000000000, 28.5, 0.005, 'EQUITY', 'STOCK', 'USD', 'OVERWEIGHT', 215, true),
  ('MSFT', 'Microsoft Corp.', 'Technology', 'Software', 3100000000000, 30.2, 0.007, 'EQUITY', 'STOCK', 'USD', 'OVERWEIGHT', 420, true),
  ('JPM', 'JPMorgan Chase & Co.', 'Financials', 'Banks', 520000000000, 11.3, 0.028, 'EQUITY', 'STOCK', 'USD', 'NEUTRAL', 175, false),
  ('XOM', 'Exxon Mobil Corp.', 'Energy', 'Integrated Oil & Gas', 410000000000, 12.8, 0.032, 'EQUITY', 'STOCK', 'USD', 'UNDERWEIGHT', 95, false),
  ('JPMBOND10', 'US Treasury 10Y (Mock)', 'Fixed Income', 'Treasury', 0, 0, 0.045, 'FIXED_INCOME', 'BOND', 'USD', 'NEUTRAL', NULL, false),
  ('JPMCD12', '12M Bank CD (Mock)', 'Fixed Income', 'CD', 0, 0, 0.052, 'FIXED_INCOME', 'BOND', 'USD', 'NEUTRAL', NULL, false);

INSERT INTO portfolio_reward_offer (id, name, description, min_deposit, bonus_amount, currency, status, created_at) VALUES
  ('offer-500-50', 'Fund $500, get $50', 'Deposit at least $500 in new money to earn a $50 bonus.', 500, 50, 'USD', 'ACTIVE', NOW()),
  ('offer-2000-150', 'Fund $2,000, get $150', 'Deposit at least $2,000 in new money to earn a $150 bonus.', 2000, 150, 'USD', 'ACTIVE', NOW());

