CREATE TABLE IF NOT EXISTS portfolio_exchange_calendar (
  id varchar(128) PRIMARY KEY,
  exchange_code varchar(16) NOT NULL,
  session_date date NOT NULL,
  status varchar(32) NOT NULL,
  open_time varchar(16),
  close_time varchar(16),
  notes text,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_exchange_calendar_unique
  ON portfolio_exchange_calendar(exchange_code, session_date);

CREATE INDEX IF NOT EXISTS idx_exchange_calendar_exchange
  ON portfolio_exchange_calendar(exchange_code);
