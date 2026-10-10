CREATE TABLE IF NOT EXISTS ai_model_registry (
  id varchar(64) PRIMARY KEY,
  model_name varchar(64) NOT NULL,
  version varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  training_start timestamp with time zone,
  training_end timestamp with time zone,
  metrics_json text NOT NULL DEFAULT '{}',
  created_at timestamp with time zone NOT NULL,
  deployed_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_ai_model_name ON ai_model_registry(model_name);
CREATE INDEX IF NOT EXISTS idx_ai_model_status ON ai_model_registry(status);
