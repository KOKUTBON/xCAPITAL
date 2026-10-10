CREATE TABLE IF NOT EXISTS organizations (
  id BIGSERIAL PRIMARY KEY,
  name varchar(160) NOT NULL,
  slug varchar(160) NOT NULL,
  status varchar(32) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamp NOT NULL DEFAULT NOW(),
  updated_at timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_org_slug ON organizations(slug);

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS primary_org_id bigint,
  ADD COLUMN IF NOT EXISTS last_login_at timestamp;

ALTER TABLE users
  ADD CONSTRAINT fk_users_primary_org
  FOREIGN KEY (primary_org_id) REFERENCES organizations(id);

CREATE TABLE IF NOT EXISTS organization_members (
  id BIGSERIAL PRIMARY KEY,
  org_id bigint NOT NULL REFERENCES organizations(id),
  user_id bigint NOT NULL REFERENCES users(id),
  role varchar(32) NOT NULL,
  status varchar(32) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamp NOT NULL DEFAULT NOW(),
  updated_at timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_org_member_unique ON organization_members(org_id, user_id);
CREATE INDEX IF NOT EXISTS idx_org_member_user ON organization_members(user_id);

CREATE TABLE IF NOT EXISTS organization_invites (
  id BIGSERIAL PRIMARY KEY,
  org_id bigint NOT NULL REFERENCES organizations(id),
  email varchar(320) NOT NULL,
  role varchar(32) NOT NULL DEFAULT 'MEMBER',
  token varchar(64) NOT NULL,
  status varchar(32) NOT NULL DEFAULT 'PENDING',
  expires_at timestamp,
  created_at timestamp NOT NULL DEFAULT NOW(),
  accepted_at timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_org_invite_token ON organization_invites(token);
CREATE INDEX IF NOT EXISTS idx_org_invite_org ON organization_invites(org_id);
CREATE INDEX IF NOT EXISTS idx_org_invite_email ON organization_invites(email);

CREATE TABLE IF NOT EXISTS organization_identity_provider (
  id BIGSERIAL PRIMARY KEY,
  org_id bigint NOT NULL REFERENCES organizations(id),
  provider_type varchar(32) NOT NULL,
  issuer varchar(256),
  sso_url varchar(256),
  metadata_url varchar(256),
  client_id varchar(128),
  client_secret varchar(256),
  x509_cert text,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamp NOT NULL DEFAULT NOW(),
  updated_at timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_org_idp_unique ON organization_identity_provider(org_id, provider_type);

CREATE TABLE IF NOT EXISTS organization_scim_config (
  id BIGSERIAL PRIMARY KEY,
  org_id bigint NOT NULL REFERENCES organizations(id),
  base_url varchar(256),
  token_hash varchar(255),
  enabled boolean NOT NULL DEFAULT false,
  last_rotated_at timestamp,
  created_at timestamp NOT NULL DEFAULT NOW(),
  updated_at timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_org_scim_unique ON organization_scim_config(org_id);
