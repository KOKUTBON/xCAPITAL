CREATE TABLE IF NOT EXISTS simulation_job (
  id varchar(64) PRIMARY KEY,
  status varchar(32) NOT NULL,
  request_json text NOT NULL,
  result_json text,
  error text,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_simulation_job_status ON simulation_job(status);
CREATE INDEX IF NOT EXISTS idx_simulation_job_created ON simulation_job(created_at);
