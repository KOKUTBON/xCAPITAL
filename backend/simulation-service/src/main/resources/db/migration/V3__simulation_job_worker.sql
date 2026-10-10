ALTER TABLE simulation_job
  ADD COLUMN IF NOT EXISTS worker_id varchar(64),
  ADD COLUMN IF NOT EXISTS attempts integer,
  ADD COLUMN IF NOT EXISTS started_at timestamp with time zone,
  ADD COLUMN IF NOT EXISTS last_error text;

CREATE INDEX IF NOT EXISTS idx_simulation_job_worker ON simulation_job(worker_id);
CREATE INDEX IF NOT EXISTS idx_simulation_job_started ON simulation_job(started_at);
