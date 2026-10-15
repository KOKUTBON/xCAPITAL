CREATE TABLE IF NOT EXISTS portfolio_compliance_profile (
  user_id varchar(64) PRIMARY KEY,
  kyc_status varchar(32) NOT NULL,
  aml_status varchar(32) NOT NULL,
  suitability_status varchar(32) NOT NULL,
  risk_profile varchar(32) NOT NULL,
  account_type varchar(32) NOT NULL,
  tax_residency varchar(32) NOT NULL,
  accredited_investor boolean NOT NULL DEFAULT false,
  restrictions_json text NOT NULL DEFAULT '[]',
  updated_at timestamp with time zone NOT NULL,
  created_at timestamp with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolio_paper_account (
  user_id varchar(64) PRIMARY KEY,
  payload text NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolio_trade_proposal (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL,
  payload text NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trade_proposal_user ON portfolio_trade_proposal(user_id);
CREATE INDEX IF NOT EXISTS idx_trade_proposal_status ON portfolio_trade_proposal(status);

CREATE TABLE IF NOT EXISTS portfolio_funding_source (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  method_type varchar(32) NOT NULL,
  provider_id varchar(64),
  label varchar(128),
  last4 varchar(16),
  currency varchar(16),
  network varchar(32),
  status varchar(32) NOT NULL,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_funding_source_user ON portfolio_funding_source(user_id);

CREATE TABLE IF NOT EXISTS portfolio_funding_deposit (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  source_id varchar(64) NOT NULL,
  amount numeric(18,4) NOT NULL,
  status varchar(32) NOT NULL,
  note text,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_funding_deposit_user ON portfolio_funding_deposit(user_id);
CREATE INDEX IF NOT EXISTS idx_funding_deposit_source ON portfolio_funding_deposit(source_id);

CREATE TABLE IF NOT EXISTS portfolio_broker_account (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  provider_id varchar(64) NOT NULL,
  provider_name varchar(128) NOT NULL,
  region varchar(32) NOT NULL,
  asset_classes_json text NOT NULL,
  status varchar(32) NOT NULL,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_broker_account_user ON portfolio_broker_account(user_id);

CREATE TABLE IF NOT EXISTS portfolio_execution_intent (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  proposal_id varchar(64) NOT NULL,
  provider_id varchar(64) NOT NULL,
  provider_name varchar(128) NOT NULL,
  region varchar(32) NOT NULL,
  asset_class varchar(32) NOT NULL,
  status varchar(32) NOT NULL,
  created_at timestamp with time zone NOT NULL,
  note text,
  orders_json text NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_execution_intent_user ON portfolio_execution_intent(user_id);
CREATE INDEX IF NOT EXISTS idx_execution_intent_proposal ON portfolio_execution_intent(proposal_id);

CREATE TABLE IF NOT EXISTS portfolio_market_price (
  id varchar(128) PRIMARY KEY,
  symbol varchar(32) NOT NULL,
  ts timestamp with time zone NOT NULL,
  open double precision NOT NULL,
  high double precision NOT NULL,
  low double precision NOT NULL,
  close double precision NOT NULL,
  volume double precision,
  source varchar(64) NOT NULL,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_market_price_symbol_ts ON portfolio_market_price(symbol, ts);
