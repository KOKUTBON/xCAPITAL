CREATE TABLE IF NOT EXISTS portfolio_exchange (
  code varchar(16) PRIMARY KEY,
  name varchar(128) NOT NULL,
  region varchar(32) NOT NULL,
  timezone varchar(64) NOT NULL,
  mic varchar(16),
  currency varchar(16),
  open_time varchar(16),
  close_time varchar(16),
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolio_currency (
  code varchar(16) PRIMARY KEY,
  name varchar(64) NOT NULL,
  symbol varchar(8),
  decimals integer NOT NULL DEFAULT 2,
  created_at timestamp with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS portfolio_fx_rate (
  id varchar(64) PRIMARY KEY,
  base_ccy varchar(16) NOT NULL,
  quote_ccy varchar(16) NOT NULL,
  rate double precision NOT NULL,
  ts timestamp with time zone NOT NULL,
  source varchar(64) NOT NULL,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fx_rate_pair_ts ON portfolio_fx_rate(base_ccy, quote_ccy, ts);

CREATE TABLE IF NOT EXISTS portfolio_instrument (
  id varchar(64) PRIMARY KEY,
  symbol varchar(64) NOT NULL,
  name varchar(128) NOT NULL,
  asset_class varchar(32) NOT NULL,
  instrument_type varchar(32) NOT NULL,
  exchange_code varchar(16),
  currency varchar(16) NOT NULL,
  status varchar(32) NOT NULL,
  external_ids_json text,
  metadata_json text,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_instrument_symbol ON portfolio_instrument(symbol);
CREATE INDEX IF NOT EXISTS idx_instrument_asset_class ON portfolio_instrument(asset_class);

CREATE TABLE IF NOT EXISTS portfolio_broker_connection (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  broker_id varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  label varchar(128),
  metadata_json text NOT NULL DEFAULT '{}',
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL,
  last_synced_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_broker_conn_user ON portfolio_broker_connection(user_id);
CREATE INDEX IF NOT EXISTS idx_broker_conn_broker ON portfolio_broker_connection(broker_id);

ALTER TABLE IF EXISTS portfolio_broker_account
  ADD COLUMN IF NOT EXISTS broker_connection_id varchar(64),
  ADD COLUMN IF NOT EXISTS external_account_id varchar(64),
  ADD COLUMN IF NOT EXISTS account_number varchar(64),
  ADD COLUMN IF NOT EXISTS base_currency varchar(16),
  ADD COLUMN IF NOT EXISTS account_type varchar(32),
  ADD COLUMN IF NOT EXISTS permissions_json text,
  ADD COLUMN IF NOT EXISTS balances_json text,
  ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone;

CREATE INDEX IF NOT EXISTS idx_broker_account_conn ON portfolio_broker_account(broker_connection_id);

CREATE TABLE IF NOT EXISTS portfolio_broker_position (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  broker_account_id varchar(64) NOT NULL,
  instrument_id varchar(64),
  symbol varchar(64) NOT NULL,
  asset_class varchar(32) NOT NULL,
  quantity double precision NOT NULL,
  avg_price double precision,
  market_price double precision,
  market_value double precision,
  cost_basis double precision,
  unrealized_pnl double precision,
  currency varchar(16) NOT NULL,
  metadata_json text,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_broker_position_user ON portfolio_broker_position(user_id);
CREATE INDEX IF NOT EXISTS idx_broker_position_account ON portfolio_broker_position(broker_account_id);

CREATE TABLE IF NOT EXISTS portfolio_broker_order (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  broker_account_id varchar(64) NOT NULL,
  external_order_id varchar(64),
  status varchar(32) NOT NULL,
  order_type varchar(32) NOT NULL,
  side varchar(16) NOT NULL,
  time_in_force varchar(16),
  submitted_at timestamp with time zone,
  updated_at timestamp with time zone,
  filled_at timestamp with time zone,
  total_quantity double precision,
  filled_quantity double precision,
  avg_price double precision,
  currency varchar(16),
  metadata_json text
);

CREATE INDEX IF NOT EXISTS idx_broker_order_user ON portfolio_broker_order(user_id);
CREATE INDEX IF NOT EXISTS idx_broker_order_account ON portfolio_broker_order(broker_account_id);

CREATE TABLE IF NOT EXISTS portfolio_broker_order_leg (
  id varchar(64) PRIMARY KEY,
  order_id varchar(64) NOT NULL,
  instrument_id varchar(64),
  symbol varchar(64) NOT NULL,
  asset_class varchar(32) NOT NULL,
  side varchar(16) NOT NULL,
  quantity double precision NOT NULL,
  limit_price double precision,
  stop_price double precision,
  option_type varchar(8),
  strike double precision,
  expiry date,
  metadata_json text
);

CREATE INDEX IF NOT EXISTS idx_broker_order_leg_order ON portfolio_broker_order_leg(order_id);
