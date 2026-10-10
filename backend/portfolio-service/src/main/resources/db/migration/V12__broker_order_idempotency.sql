ALTER TABLE portfolio_broker_order
  ADD COLUMN IF NOT EXISTS client_order_id varchar(128);

CREATE UNIQUE INDEX IF NOT EXISTS idx_broker_order_idempotency
  ON portfolio_broker_order(user_id, broker_account_id, client_order_id)
  WHERE client_order_id IS NOT NULL;
