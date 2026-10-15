ALTER TABLE users
  ADD COLUMN IF NOT EXISTS status varchar(32) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN IF NOT EXISTS disabled_at timestamp;

ALTER TABLE organization_identity_provider
  ADD COLUMN IF NOT EXISTS authorization_url varchar(256),
  ADD COLUMN IF NOT EXISTS token_url varchar(256),
  ADD COLUMN IF NOT EXISTS jwks_url varchar(256),
  ADD COLUMN IF NOT EXISTS scopes varchar(256),
  ADD COLUMN IF NOT EXISTS redirect_url varchar(256);

CREATE TABLE IF NOT EXISTS federated_identity (
  id BIGSERIAL PRIMARY KEY,
  org_id bigint NOT NULL REFERENCES organizations(id),
  provider_id bigint NOT NULL REFERENCES organization_identity_provider(id),
  external_subject varchar(256) NOT NULL,
  email varchar(320),
  user_id bigint NOT NULL REFERENCES users(id),
  created_at timestamp NOT NULL DEFAULT NOW(),
  updated_at timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_federated_identity_subject ON federated_identity(provider_id, external_subject);
CREATE INDEX IF NOT EXISTS idx_federated_identity_user ON federated_identity(user_id);
CREATE INDEX IF NOT EXISTS idx_federated_identity_org ON federated_identity(org_id);

CREATE TABLE IF NOT EXISTS sso_login_session (
  id varchar(64) PRIMARY KEY,
  org_id bigint NOT NULL REFERENCES organizations(id),
  provider_id bigint NOT NULL REFERENCES organization_identity_provider(id),
  flow_type varchar(16) NOT NULL,
  state varchar(128) NOT NULL,
  nonce varchar(128),
  code_verifier varchar(256),
  request_id varchar(128),
  redirect_uri varchar(256),
  created_at timestamp NOT NULL DEFAULT NOW(),
  expires_at timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sso_session_state ON sso_login_session(state);
CREATE INDEX IF NOT EXISTS idx_sso_session_org ON sso_login_session(org_id);
