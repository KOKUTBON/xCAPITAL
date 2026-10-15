ALTER TABLE IF EXISTS portfolio_audit_event
  ADD COLUMN IF NOT EXISTS prev_hash text,
  ADD COLUMN IF NOT EXISTS event_hash text;

CREATE INDEX IF NOT EXISTS idx_audit_event_hash ON portfolio_audit_event(event_hash);
