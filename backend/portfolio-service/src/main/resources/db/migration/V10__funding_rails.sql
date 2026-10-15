ALTER TABLE portfolio_funding_deposit
  ADD COLUMN IF NOT EXISTS currency varchar(16),
  ADD COLUMN IF NOT EXISTS provider_id varchar(64),
  ADD COLUMN IF NOT EXISTS provider_reference varchar(128),
  ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone;

CREATE TABLE IF NOT EXISTS portfolio_funding_withdrawal (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  source_id varchar(64) NOT NULL,
  amount double precision NOT NULL,
  currency varchar(16),
  status varchar(32) NOT NULL,
  note text,
  provider_id varchar(64),
  provider_reference varchar(128),
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_funding_withdrawal_user ON portfolio_funding_withdrawal(user_id);
CREATE INDEX IF NOT EXISTS idx_funding_withdrawal_source ON portfolio_funding_withdrawal(source_id);

CREATE TABLE IF NOT EXISTS portfolio_funding_transfer (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  source_id varchar(64) NOT NULL,
  broker_account_id varchar(64) NOT NULL,
  direction varchar(32) NOT NULL,
  amount double precision NOT NULL,
  currency varchar(16),
  status varchar(32) NOT NULL,
  note text,
  provider_id varchar(64),
  provider_reference varchar(128),
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_funding_transfer_user ON portfolio_funding_transfer(user_id);
CREATE INDEX IF NOT EXISTS idx_funding_transfer_source ON portfolio_funding_transfer(source_id);
CREATE INDEX IF NOT EXISTS idx_funding_transfer_broker ON portfolio_funding_transfer(broker_account_id);
