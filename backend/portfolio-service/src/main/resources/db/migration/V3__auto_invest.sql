CREATE TABLE IF NOT EXISTS portfolio_auto_invest_plan (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  name varchar(128) NOT NULL,
  status varchar(32) NOT NULL,
  schedule varchar(32) NOT NULL,
  schedule_time_utc varchar(8),
  schedule_day_of_week varchar(16),
  drift_threshold double precision,
  returns_lookback integer,
  use_market_data boolean NOT NULL DEFAULT true,
  use_ai_forecast boolean NOT NULL DEFAULT false,
  ai_horizon integer,
  method varchar(32),
  risk_aversion integer NOT NULL,
  max_weight double precision NOT NULL,
  min_weight double precision NOT NULL,
  min_trade_value double precision,
  max_trade_pct double precision,
  max_turnover double precision,
  execution_mode varchar(32),
  region varchar(32),
  asset_class varchar(32),
  provider_preference varchar(64),
  order_type varchar(32),
  time_in_force varchar(32),
  symbols_json text NOT NULL,
  mu_json text,
  cov_json text,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL,
  last_run_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_auto_invest_plan_user ON portfolio_auto_invest_plan(user_id);
CREATE INDEX IF NOT EXISTS idx_auto_invest_plan_status ON portfolio_auto_invest_plan(status);

CREATE TABLE IF NOT EXISTS portfolio_auto_invest_run (
  id varchar(64) PRIMARY KEY,
  plan_id varchar(64) NOT NULL,
  user_id varchar(64) NOT NULL,
  trigger varchar(32) NOT NULL,
  status varchar(32) NOT NULL,
  idempotency_key varchar(128) NOT NULL,
  proposal_id varchar(64),
  reason text,
  metrics_json text,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_auto_invest_run_idempotency ON portfolio_auto_invest_run(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_auto_invest_run_plan ON portfolio_auto_invest_run(plan_id);
CREATE INDEX IF NOT EXISTS idx_auto_invest_run_user ON portfolio_auto_invest_run(user_id);

CREATE TABLE IF NOT EXISTS portfolio_notification (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  type varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  title varchar(128) NOT NULL,
  body text NOT NULL,
  entity_type varchar(64),
  entity_id varchar(64),
  metadata_json text NOT NULL DEFAULT '{}',
  created_at timestamp with time zone NOT NULL,
  read_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_notification_user ON portfolio_notification(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_status ON portfolio_notification(status);
