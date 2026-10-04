-- Phase 1: channel connection, encrypted credentials, status map, audit.
-- Does not alter shop, stock, order, or payment databases.
-- Does not change marketplace_order status values.

ALTER TABLE marketplace_channel
    ADD COLUMN connection_status VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED';

ALTER TABLE marketplace_channel
    ADD CONSTRAINT ck_mp_channel_connection_status
        CHECK (connection_status IN ('DISCONNECTED', 'CONNECTED', 'ERROR', 'TOKEN_EXPIRED'));

ALTER TABLE marketplace_channel
    ADD COLUMN credentials_ciphertext TEXT NULL;

CREATE TABLE channel_status_mapping (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        VARCHAR(64)  NOT NULL,
    channel_id       BIGINT       NOT NULL REFERENCES marketplace_channel(id),
    external_status  VARCHAR(64)  NOT NULL,
    internal_status  VARCHAR(32)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_channel_status_internal CHECK (internal_status IN (
        'NEW', 'CONFIRMED', 'ALLOCATED', 'PACKED', 'READY_TO_SHIP', 'SHIPPED',
        'DELIVERED', 'CANCELLED', 'RETURN_REQUESTED', 'RETURNED', 'REFUNDED', 'FAILED'
    ))
);

CREATE UNIQUE INDEX uq_channel_status_external
    ON channel_status_mapping (channel_id, lower(external_status));

CREATE INDEX idx_channel_status_tenant
    ON channel_status_mapping (tenant_id, channel_id);

CREATE TABLE marketplace_channel_audit (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL,
    shop_id      VARCHAR(64)  NULL,
    channel_id   BIGINT       NOT NULL REFERENCES marketplace_channel(id),
    action       VARCHAR(32)  NOT NULL,
    actor        VARCHAR(128) NULL,
    detail_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mp_channel_audit_action CHECK (action IN (
        'CHANNEL_SAVED', 'CONNECTED', 'DISCONNECTED', 'STATUS_MAP_SAVED', 'SECRETS_UPDATED'
    ))
);

CREATE INDEX idx_mp_channel_audit_channel
    ON marketplace_channel_audit (tenant_id, channel_id, created_at DESC);
