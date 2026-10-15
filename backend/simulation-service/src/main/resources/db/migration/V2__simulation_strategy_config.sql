CREATE TABLE IF NOT EXISTS simulation_strategy_config (
  id varchar(64) PRIMARY KEY,
  strategy varchar(32) NOT NULL,
  version integer NOT NULL,
  hash varchar(128) NOT NULL,
  config_json text NOT NULL,
  created_at timestamp with time zone NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_strategy_config_hash ON simulation_strategy_config(hash);
CREATE INDEX IF NOT EXISTS idx_strategy_config_strategy_version ON simulation_strategy_config(strategy, version);

ALTER TABLE IF EXISTS simulation_job
  ADD COLUMN IF NOT EXISTS strategy_config_id varchar(64),
  ADD COLUMN IF NOT EXISTS strategy_config_version integer,
  ADD COLUMN IF NOT EXISTS returns_hash varchar(128);
