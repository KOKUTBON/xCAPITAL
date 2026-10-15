CREATE TABLE IF NOT EXISTS portfolio_notification_destination (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  channel varchar(32) NOT NULL,
  destination text NOT NULL,
  label varchar(64),
  status varchar(32) NOT NULL,
  created_at timestamp with time zone NOT NULL,
  verified_at timestamp with time zone,
  updated_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_notification_destination_user ON portfolio_notification_destination(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_destination_channel ON portfolio_notification_destination(channel);

CREATE TABLE IF NOT EXISTS portfolio_notification_preference (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  channel varchar(32) NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  types_json text NOT NULL DEFAULT '[]',
  quiet_start_hour int,
  quiet_end_hour int,
  timezone varchar(64),
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_notification_pref_user_channel ON portfolio_notification_preference(user_id, channel);

CREATE TABLE IF NOT EXISTS portfolio_notification_delivery (
  id varchar(64) PRIMARY KEY,
  notification_id varchar(64) NOT NULL,
  user_id varchar(64) NOT NULL,
  channel varchar(32) NOT NULL,
  destination_id varchar(64),
  status varchar(32) NOT NULL,
  provider varchar(64),
  attempt_count int NOT NULL DEFAULT 0,
  last_error text,
  last_attempt_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_user ON portfolio_notification_delivery(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_delivery_notification ON portfolio_notification_delivery(notification_id);
CREATE INDEX IF NOT EXISTS idx_notification_delivery_status ON portfolio_notification_delivery(status);
