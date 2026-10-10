ALTER TABLE IF EXISTS portfolio_audit_event
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_account
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_paper_account
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_broker_account
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_broker_connection
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_banking_account
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_funding_source
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_auto_invest_plan
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_wealth_plan
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_watchlist
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_reward_enrollment
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_notification_preference
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_compliance_profile
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_audit_event_org ON portfolio_audit_event(org_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_account_org ON portfolio_account(org_id);
CREATE INDEX IF NOT EXISTS idx_broker_account_org ON portfolio_broker_account(org_id);
CREATE INDEX IF NOT EXISTS idx_broker_connection_org ON portfolio_broker_connection(org_id);
CREATE INDEX IF NOT EXISTS idx_banking_account_org ON portfolio_banking_account(org_id);
CREATE INDEX IF NOT EXISTS idx_funding_source_org ON portfolio_funding_source(org_id);
CREATE INDEX IF NOT EXISTS idx_auto_invest_plan_org ON portfolio_auto_invest_plan(org_id);
CREATE INDEX IF NOT EXISTS idx_wealth_plan_org ON portfolio_wealth_plan(org_id);
CREATE INDEX IF NOT EXISTS idx_watchlist_org ON portfolio_watchlist(org_id);
CREATE INDEX IF NOT EXISTS idx_reward_enrollment_org ON portfolio_reward_enrollment(org_id);
CREATE INDEX IF NOT EXISTS idx_notification_pref_org ON portfolio_notification_preference(org_id);
CREATE INDEX IF NOT EXISTS idx_compliance_profile_org ON portfolio_compliance_profile(org_id);
