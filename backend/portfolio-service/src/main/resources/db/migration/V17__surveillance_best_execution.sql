CREATE TABLE IF NOT EXISTS portfolio_surveillance_alert (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  alert_type varchar(64) NOT NULL,
  severity varchar(32) NOT NULL,
  symbol varchar(32),
  notional double precision,
  detail text,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_surveillance_user ON portfolio_surveillance_alert(user_id);

CREATE TABLE IF NOT EXISTS portfolio_best_execution (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  proposal_id varchar(64),
  symbol varchar(32) NOT NULL,
  side varchar(16) NOT NULL,
  requested_price double precision,
  executed_price double precision,
  market_price double precision,
  slippage_bps double precision,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_best_execution_user ON portfolio_best_execution(user_id);
CREATE INDEX IF NOT EXISTS idx_best_execution_symbol ON portfolio_best_execution(symbol);
