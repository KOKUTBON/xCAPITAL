CREATE TABLE IF NOT EXISTS portfolio_market_data_license (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  provider varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  plan varchar(64),
  asset_classes_json text NOT NULL DEFAULT '[]',
  exchanges_json text NOT NULL DEFAULT '[]',
  regions_json text NOT NULL DEFAULT '[]',
  starts_at timestamp with time zone,
  ends_at timestamp with time zone,
  metadata_json text NOT NULL DEFAULT '{}',
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_md_license_user ON portfolio_market_data_license(user_id);
CREATE INDEX IF NOT EXISTS idx_md_license_status ON portfolio_market_data_license(status);

CREATE TABLE IF NOT EXISTS portfolio_market_data_entitlement (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  entitlement_type varchar(32) NOT NULL,
  entitlement_value varchar(128),
  status varchar(32) NOT NULL,
  source varchar(64),
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_md_entitlement_unique ON portfolio_market_data_entitlement(user_id, entitlement_type, entitlement_value);
CREATE INDEX IF NOT EXISTS idx_md_entitlement_user ON portfolio_market_data_entitlement(user_id);
CREATE INDEX IF NOT EXISTS idx_md_entitlement_status ON portfolio_market_data_entitlement(status);
