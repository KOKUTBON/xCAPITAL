ALTER TABLE users
  ADD COLUMN IF NOT EXISTS roles varchar(256) DEFAULT 'USER',
  ADD COLUMN IF NOT EXISTS mfa_enabled boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS mfa_method varchar(32),
  ADD COLUMN IF NOT EXISTS mfa_secret varchar(128),
  ADD COLUMN IF NOT EXISTS mfa_enrolled_at timestamp,
  ADD COLUMN IF NOT EXISTS mfa_verified_at timestamp;
