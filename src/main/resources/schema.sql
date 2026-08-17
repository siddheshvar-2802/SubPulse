-- ═══════════════════════════════════════════════════════════════════════════════
-- SubPulse — Database Schema Definition (PostgreSQL)
-- Schema: subpulse
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS subpulse;
SET search_path TO subpulse, public;

-- ── 1. Users Table ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subpulse.users (
    id                  BIGSERIAL PRIMARY KEY,
    full_name           VARCHAR(255) NOT NULL,
    email               VARCHAR(150) NOT NULL UNIQUE,
    password_hash       VARCHAR(255),
    oauth2_provider     VARCHAR(30),
    oauth2_provider_id  VARCHAR(200),
    timezone            VARCHAR(60)  NOT NULL DEFAULT 'UTC',
    preferred_currency  VARCHAR(10)  NOT NULL DEFAULT 'USD',
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON subpulse.users (email);

-- ── 2. Subscriptions Table ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subpulse.subscriptions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT         NOT NULL REFERENCES subpulse.users(id) ON DELETE CASCADE,
    service_name        VARCHAR(100)   NOT NULL,
    description         VARCHAR(500),
    website_url         VARCHAR(300),
    amount              NUMERIC(10, 2) NOT NULL,
    currency            VARCHAR(10)    NOT NULL DEFAULT 'USD',
    billing_cycle       VARCHAR(20)    NOT NULL,
    start_date          DATE           NOT NULL,
    next_billing_date   DATE           NOT NULL,
    trial_end_date      DATE,
    is_active           BOOLEAN        NOT NULL DEFAULT TRUE,
    auto_renew          BOOLEAN        NOT NULL DEFAULT TRUE,
    category            VARCHAR(30)    DEFAULT 'OTHER',
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sub_user_id           ON subpulse.subscriptions (user_id);
CREATE INDEX IF NOT EXISTS idx_sub_next_billing_date ON subpulse.subscriptions (next_billing_date);
CREATE INDEX IF NOT EXISTS idx_sub_is_active         ON subpulse.subscriptions (is_active);

-- ── 3. Alert Configs Table ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subpulse.alert_configs (
    id                  BIGSERIAL PRIMARY KEY,
    subscription_id     BIGINT       NOT NULL REFERENCES subpulse.subscriptions(id) ON DELETE CASCADE,
    days_before         INTEGER      NOT NULL CHECK (days_before >= 1 AND days_before <= 30),
    channel             VARCHAR(20)  NOT NULL,
    is_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    destination         VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_alert_sub_channel_days UNIQUE (subscription_id, channel, days_before)
);

CREATE INDEX IF NOT EXISTS idx_alert_subscription_id ON subpulse.alert_configs (subscription_id);
CREATE INDEX IF NOT EXISTS idx_alert_channel         ON subpulse.alert_configs (channel);

-- ── 4. Notification Logs Table (Append-Only Audit) ───────────────────────────
CREATE TABLE IF NOT EXISTS subpulse.notification_logs (
    id                  BIGSERIAL PRIMARY KEY,
    subscription_id     BIGINT       NOT NULL REFERENCES subpulse.subscriptions(id) ON DELETE CASCADE,
    channel             VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    sent_at             TIMESTAMP,
    days_remaining      INTEGER,
    error_message       VARCHAR(1000),
    message_body        VARCHAR(2000),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notif_subscription_id ON subpulse.notification_logs (subscription_id);
CREATE INDEX IF NOT EXISTS idx_notif_status          ON subpulse.notification_logs (status);
CREATE INDEX IF NOT EXISTS idx_notif_sent_at         ON subpulse.notification_logs (sent_at);

-- ── 5. ShedLock Table (Distributed Lock Storage) ─────────────────────────────
CREATE TABLE IF NOT EXISTS subpulse.shedlock (
    name                VARCHAR(64)  PRIMARY KEY,
    lock_until          TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by           VARCHAR(255) NOT NULL
);
