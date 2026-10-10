-- Legacy approvals/intents are deliberately not granted new authorization evidence.
ALTER TABLE portfolio_trade_proposal ADD COLUMN approved_by varchar(64);
ALTER TABLE portfolio_trade_proposal ADD COLUMN approved_at timestamp with time zone;
ALTER TABLE portfolio_trade_proposal ADD COLUMN approval_hash varchar(64);
ALTER TABLE portfolio_execution_intent ADD COLUMN approval_hash varchar(64);
ALTER TABLE portfolio_execution_intent ADD COLUMN broker_account_id varchar(64);

-- Legacy duplicate intents can remain for investigation; new authorized intents are unique.
CREATE UNIQUE INDEX idx_execution_authorized_proposal
  ON portfolio_execution_intent(proposal_id) WHERE approval_hash IS NOT NULL;
