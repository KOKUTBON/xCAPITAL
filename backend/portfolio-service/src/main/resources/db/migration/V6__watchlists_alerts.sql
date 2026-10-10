CREATE TABLE IF NOT EXISTS portfolio_watchlist (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  name varchar(128) NOT NULL,
  description text,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_watchlist_user ON portfolio_watchlist(user_id);

CREATE TABLE IF NOT EXISTS portfolio_watchlist_item (
  id varchar(64) PRIMARY KEY,
  watchlist_id varchar(64) NOT NULL,
  symbol varchar(64) NOT NULL,
  instrument_id varchar(64),
  asset_class varchar(32),
  notes text,
  ai_score double precision,
  ai_summary text,
  metadata_json text,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_watchlist_item_watchlist ON portfolio_watchlist_item(watchlist_id);
CREATE INDEX IF NOT EXISTS idx_watchlist_item_symbol ON portfolio_watchlist_item(symbol);

CREATE TABLE IF NOT EXISTS portfolio_alert (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  alert_type varchar(32) NOT NULL,
  symbol varchar(64),
  instrument_id varchar(64),
  asset_class varchar(32),
  comparison varchar(16),
  target_value double precision,
  frequency varchar(16),
  condition_json text,
  ai_score double precision,
  ai_summary text,
  metadata_json text,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL,
  last_triggered_at timestamp with time zone,
  last_checked_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_alert_user ON portfolio_alert(user_id);
CREATE INDEX IF NOT EXISTS idx_alert_status ON portfolio_alert(status);
