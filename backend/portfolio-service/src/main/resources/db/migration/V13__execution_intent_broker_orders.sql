ALTER TABLE portfolio_execution_intent
  ADD COLUMN IF NOT EXISTS broker_order_ids_json text;
