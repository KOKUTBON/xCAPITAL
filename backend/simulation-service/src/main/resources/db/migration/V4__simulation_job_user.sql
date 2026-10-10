ALTER TABLE simulation_job
  ADD COLUMN IF NOT EXISTS user_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_simulation_job_user_status ON simulation_job(user_id, status);
