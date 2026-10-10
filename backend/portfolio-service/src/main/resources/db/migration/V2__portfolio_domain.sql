CREATE TABLE IF NOT EXISTS portfolio_account (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  account_type varchar(32) NOT NULL,
  status varchar(32) NOT NULL,
  cash double precision NOT NULL,
  currency varchar(16) NOT NULL,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_portfolio_account_user ON portfolio_account(user_id);

CREATE TABLE IF NOT EXISTS portfolio_position (
  id varchar(64) PRIMARY KEY,
  account_id varchar(64) NOT NULL,
  symbol varchar(32) NOT NULL,
  quantity double precision NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_position_account_symbol ON portfolio_position(account_id, symbol);
CREATE INDEX IF NOT EXISTS idx_position_account ON portfolio_position(account_id);

ALTER TABLE IF EXISTS portfolio_trade_proposal
  ADD COLUMN IF NOT EXISTS account_id varchar(64),
  ADD COLUMN IF NOT EXISTS execution_mode varchar(32),
  ADD COLUMN IF NOT EXISTS region varchar(32),
  ADD COLUMN IF NOT EXISTS asset_class varchar(32),
  ADD COLUMN IF NOT EXISTS provider_preference varchar(64),
  ADD COLUMN IF NOT EXISTS order_type varchar(32),
  ADD COLUMN IF NOT EXISTS time_in_force varchar(32),
  ADD COLUMN IF NOT EXISTS expected_return double precision,
  ADD COLUMN IF NOT EXISTS variance double precision,
  ADD COLUMN IF NOT EXISTS total_equity double precision,
  ADD COLUMN IF NOT EXISTS turnover double precision,
  ADD COLUMN IF NOT EXISTS scaled_buy_factor double precision,
  ADD COLUMN IF NOT EXISTS fee_bps double precision,
  ADD COLUMN IF NOT EXISTS fee_total double precision,
  ADD COLUMN IF NOT EXISTS ai_summary text,
  ADD COLUMN IF NOT EXISTS ai_confidence double precision,
  ADD COLUMN IF NOT EXISTS ai_expected_return double precision,
  ADD COLUMN IF NOT EXISTS ai_volatility double precision,
  ADD COLUMN IF NOT EXISTS ai_p_up double precision,
  ADD COLUMN IF NOT EXISTS ai_horizon integer,
  ADD COLUMN IF NOT EXISTS ai_model varchar(64),
  ADD COLUMN IF NOT EXISTS ai_disclaimer text,
  ADD COLUMN IF NOT EXISTS disclaimer text,
  ADD COLUMN IF NOT EXISTS execution_intent_id varchar(64),
  ADD COLUMN IF NOT EXISTS execution_json text,
  ADD COLUMN IF NOT EXISTS symbols_json text,
  ADD COLUMN IF NOT EXISTS prices_json text,
  ADD COLUMN IF NOT EXISTS current_weights_json text,
  ADD COLUMN IF NOT EXISTS target_weights_json text,
  ADD COLUMN IF NOT EXISTS policy_checks_json text;

CREATE TABLE IF NOT EXISTS portfolio_trade_order (
  id varchar(64) PRIMARY KEY,
  proposal_id varchar(64) NOT NULL,
  symbol varchar(32) NOT NULL,
  side varchar(16) NOT NULL,
  quantity double precision NOT NULL,
  price double precision NOT NULL,
  notional double precision NOT NULL,
  fee double precision NOT NULL,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trade_order_proposal ON portfolio_trade_order(proposal_id);

CREATE TABLE IF NOT EXISTS portfolio_execution_fill (
  id varchar(64) PRIMARY KEY,
  intent_id varchar(64) NOT NULL,
  user_id varchar(64) NOT NULL,
  proposal_id varchar(64) NOT NULL,
  symbol varchar(32) NOT NULL,
  side varchar(16) NOT NULL,
  quantity double precision NOT NULL,
  price double precision NOT NULL,
  fee double precision NOT NULL,
  status varchar(32) NOT NULL,
  filled_at timestamp with time zone NOT NULL,
  created_at timestamp with time zone NOT NULL,
  note text
);

CREATE INDEX IF NOT EXISTS idx_execution_fill_intent ON portfolio_execution_fill(intent_id);
CREATE INDEX IF NOT EXISTS idx_execution_fill_user ON portfolio_execution_fill(user_id);

CREATE TABLE IF NOT EXISTS portfolio_audit_event (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  actor varchar(64) NOT NULL,
  event_type varchar(64) NOT NULL,
  entity_type varchar(64),
  entity_id varchar(64),
  created_at timestamp with time zone NOT NULL,
  metadata_json text NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_audit_event_user ON portfolio_audit_event(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON portfolio_audit_event(event_type);
