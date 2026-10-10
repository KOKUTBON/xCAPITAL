CREATE TABLE IF NOT EXISTS portfolio_ledger_entry (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  account_id varchar(64) NOT NULL,
  broker_account_id varchar(64),
  entry_type varchar(32) NOT NULL,
  symbol varchar(64),
  instrument_id varchar(64),
  asset_class varchar(32),
  quantity double precision,
  price double precision,
  amount double precision,
  currency varchar(16),
  fx_rate double precision,
  trade_date timestamp with time zone,
  settle_date timestamp with time zone,
  description text,
  metadata_json text,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ledger_user ON portfolio_ledger_entry(user_id);
CREATE INDEX IF NOT EXISTS idx_ledger_account_date ON portfolio_ledger_entry(account_id, trade_date);

CREATE TABLE IF NOT EXISTS portfolio_tax_lot (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  account_id varchar(64) NOT NULL,
  symbol varchar(64) NOT NULL,
  instrument_id varchar(64),
  asset_class varchar(32),
  quantity double precision NOT NULL,
  cost_basis double precision,
  cost_per_unit double precision,
  acquired_at timestamp with time zone,
  disposed_at timestamp with time zone,
  status varchar(32) NOT NULL,
  metadata_json text,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tax_lot_user ON portfolio_tax_lot(user_id);
CREATE INDEX IF NOT EXISTS idx_tax_lot_account ON portfolio_tax_lot(account_id);
CREATE INDEX IF NOT EXISTS idx_tax_lot_symbol ON portfolio_tax_lot(symbol);

CREATE TABLE IF NOT EXISTS portfolio_corporate_action (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  account_id varchar(64),
  action_type varchar(32) NOT NULL,
  symbol varchar(64),
  instrument_id varchar(64),
  ratio double precision,
  cash_amount double precision,
  effective_date date,
  metadata_json text,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_corp_action_user ON portfolio_corporate_action(user_id);
CREATE INDEX IF NOT EXISTS idx_corp_action_symbol ON portfolio_corporate_action(symbol);

CREATE TABLE IF NOT EXISTS portfolio_statement (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  account_id varchar(64) NOT NULL,
  period_start timestamp with time zone NOT NULL,
  period_end timestamp with time zone NOT NULL,
  base_currency varchar(16) NOT NULL,
  starting_balance double precision,
  ending_balance double precision,
  deposits double precision,
  withdrawals double precision,
  dividends double precision,
  fees double precision,
  realized_pnl double precision,
  unrealized_pnl double precision,
  net_cash_flow double precision,
  trade_notional double precision,
  metadata_json text,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_statement_user ON portfolio_statement(user_id);
CREATE INDEX IF NOT EXISTS idx_statement_account ON portfolio_statement(account_id);

CREATE TABLE IF NOT EXISTS portfolio_research_note (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  source varchar(64) NOT NULL,
  headline varchar(256) NOT NULL,
  summary text,
  symbols_json text,
  sentiment_score double precision,
  confidence double precision,
  ai_score double precision,
  ai_summary text,
  published_at timestamp with time zone,
  metadata_json text,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_research_user ON portfolio_research_note(user_id);
CREATE INDEX IF NOT EXISTS idx_research_source ON portfolio_research_note(source);
