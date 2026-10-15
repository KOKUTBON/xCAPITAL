ALTER TABLE IF EXISTS portfolio_alert
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_auto_invest_fee
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_auto_invest_run
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_banking_transfer
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_best_execution
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_broker_order
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_broker_position
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_corporate_action
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_execution_fill
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_execution_intent
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_funding_deposit
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_funding_transfer
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_funding_withdrawal
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_ledger_entry
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_market_data_entitlement
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_market_data_license
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_notification
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_notification_delivery
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_notification_destination
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_research_note
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_statement
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_surveillance_alert
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_tax_lot
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

ALTER TABLE IF EXISTS portfolio_trade_proposal
  ADD COLUMN IF NOT EXISTS org_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_alert_org ON portfolio_alert(org_id);
CREATE INDEX IF NOT EXISTS idx_auto_invest_fee_org ON portfolio_auto_invest_fee(org_id);
CREATE INDEX IF NOT EXISTS idx_auto_invest_run_org ON portfolio_auto_invest_run(org_id);
CREATE INDEX IF NOT EXISTS idx_banking_transfer_org ON portfolio_banking_transfer(org_id);
CREATE INDEX IF NOT EXISTS idx_best_execution_org ON portfolio_best_execution(org_id);
CREATE INDEX IF NOT EXISTS idx_broker_order_org ON portfolio_broker_order(org_id);
CREATE INDEX IF NOT EXISTS idx_broker_position_org ON portfolio_broker_position(org_id);
CREATE INDEX IF NOT EXISTS idx_execution_intent_org ON portfolio_execution_intent(org_id);
CREATE INDEX IF NOT EXISTS idx_execution_fill_org ON portfolio_execution_fill(org_id);
CREATE INDEX IF NOT EXISTS idx_funding_deposit_org ON portfolio_funding_deposit(org_id);
CREATE INDEX IF NOT EXISTS idx_funding_transfer_org ON portfolio_funding_transfer(org_id);
CREATE INDEX IF NOT EXISTS idx_funding_withdrawal_org ON portfolio_funding_withdrawal(org_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entry_org ON portfolio_ledger_entry(org_id);
CREATE INDEX IF NOT EXISTS idx_market_data_entitlement_org ON portfolio_market_data_entitlement(org_id);
CREATE INDEX IF NOT EXISTS idx_market_data_license_org ON portfolio_market_data_license(org_id);
CREATE INDEX IF NOT EXISTS idx_notification_org ON portfolio_notification(org_id);
CREATE INDEX IF NOT EXISTS idx_notification_delivery_org ON portfolio_notification_delivery(org_id);
CREATE INDEX IF NOT EXISTS idx_notification_destination_org ON portfolio_notification_destination(org_id);
CREATE INDEX IF NOT EXISTS idx_research_note_org ON portfolio_research_note(org_id);
CREATE INDEX IF NOT EXISTS idx_statement_org ON portfolio_statement(org_id);
CREATE INDEX IF NOT EXISTS idx_surveillance_alert_org ON portfolio_surveillance_alert(org_id);
CREATE INDEX IF NOT EXISTS idx_tax_lot_org ON portfolio_tax_lot(org_id);
CREATE INDEX IF NOT EXISTS idx_trade_proposal_org ON portfolio_trade_proposal(org_id);
