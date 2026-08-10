-- Omnichannel marketplace schema (additive). Does not alter shop/stock/order DBs.
-- Existing ERP tenants never open this DB unless the optional module is deployed.

CREATE TABLE marketplace_account (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    shop_id         VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    settings_json   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_mp_account_status CHECK (status IN ('ACTIVE', 'PAUSED', 'DISABLED')),
    CONSTRAINT uq_mp_account_tenant_shop UNIQUE (tenant_id, shop_id)
);

CREATE INDEX idx_mp_account_tenant ON marketplace_account (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE marketplace_channel (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    account_id      BIGINT       NOT NULL REFERENCES marketplace_account(id),
    channel_code    VARCHAR(32)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    external_seller_id VARCHAR(128) NULL,
    credentials_ref VARCHAR(256) NULL,
    config_json     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    last_sync_at    TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_mp_channel_code CHECK (channel_code IN ('AMAZON', 'FLIPKART', 'SHOPIFY', 'WEBSITE', 'MEESHO', 'OTHER')),
    CONSTRAINT uq_mp_channel_account_code UNIQUE (account_id, channel_code)
);

CREATE INDEX idx_mp_channel_tenant ON marketplace_channel (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE marketplace_product_mapping (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    shop_id         VARCHAR(64)  NOT NULL,
    channel_id      BIGINT       NOT NULL REFERENCES marketplace_channel(id),
    product_id      BIGINT       NOT NULL,
    sku_code        VARCHAR(128) NULL,
    channel_listing_id VARCHAR(128) NOT NULL,
    channel_sku     VARCHAR(128) NULL,
    sync_inventory  BOOLEAN      NOT NULL DEFAULT TRUE,
    allocation_qty  NUMERIC(18, 3) NULL,
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_mp_map_channel_listing UNIQUE (channel_id, channel_listing_id)
);

CREATE INDEX idx_mp_map_product ON marketplace_product_mapping (tenant_id, shop_id, product_id)
    WHERE deleted_at IS NULL;

CREATE TABLE marketplace_inventory_sync (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    mapping_id      BIGINT       NOT NULL REFERENCES marketplace_product_mapping(id),
    physical_qty    NUMERIC(18, 3) NOT NULL DEFAULT 0,
    reserved_qty    NUMERIC(18, 3) NOT NULL DEFAULT 0,
    available_qty   NUMERIC(18, 3) NOT NULL DEFAULT 0,
    allocated_qty   NUMERIC(18, 3) NOT NULL DEFAULT 0,
    channel_qty     NUMERIC(18, 3) NULL,
    sync_status     VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    last_error      TEXT         NULL,
    synced_at       TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mp_inv_sync_status CHECK (sync_status IN ('PENDING', 'SYNCED', 'ERROR', 'SKIPPED'))
);

CREATE INDEX idx_mp_inv_sync_tenant ON marketplace_inventory_sync (tenant_id, sync_status);

CREATE TABLE marketplace_order (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    shop_id         VARCHAR(64)  NOT NULL,
    channel_id      BIGINT       NOT NULL REFERENCES marketplace_channel(id),
    external_order_id VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'NEW',
    currency        VARCHAR(8)   NOT NULL DEFAULT 'INR',
    total_amount    NUMERIC(18, 2) NULL,
    stock_reservation_key VARCHAR(128) NULL,
    erp_order_id    BIGINT       NULL,
    payload_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ordered_at      TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mp_order_status CHECK (status IN (
        'NEW', 'ACCEPTED', 'RESERVED', 'FULFILLED', 'CANCELLED', 'RETURNED', 'ERROR')),
    CONSTRAINT uq_mp_order_channel_ext UNIQUE (channel_id, external_order_id)
);

CREATE INDEX idx_mp_order_tenant ON marketplace_order (tenant_id, shop_id, status);

CREATE TABLE marketplace_order_item (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    order_id        BIGINT       NOT NULL REFERENCES marketplace_order(id) ON DELETE CASCADE,
    mapping_id      BIGINT       NULL REFERENCES marketplace_product_mapping(id),
    product_id      BIGINT       NULL,
    channel_sku     VARCHAR(128) NULL,
    title           VARCHAR(512) NULL,
    quantity        NUMERIC(18, 3) NOT NULL,
    unit_price      NUMERIC(18, 2) NULL,
    line_json       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mp_order_item_order ON marketplace_order_item (order_id);

CREATE TABLE marketplace_webhook_event (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NULL,
    channel_code    VARCHAR(32)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    external_id     VARCHAR(128) NULL,
    payload_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    processed       BOOLEAN      NOT NULL DEFAULT FALSE,
    process_error   TEXT         NULL,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ  NULL
);

CREATE INDEX idx_mp_webhook_unprocessed ON marketplace_webhook_event (channel_code, processed, received_at);

CREATE TABLE marketplace_sync_log (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    shop_id         VARCHAR(64)  NULL,
    channel_id      BIGINT       NULL REFERENCES marketplace_channel(id),
    sync_type       VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'OK',
    message         TEXT         NULL,
    details_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mp_sync_type CHECK (sync_type IN ('INVENTORY', 'ORDERS', 'CATALOG', 'RETURNS', 'SETTLEMENT')),
    CONSTRAINT ck_mp_sync_status CHECK (status IN ('OK', 'ERROR', 'PARTIAL'))
);

CREATE INDEX idx_mp_sync_log_tenant ON marketplace_sync_log (tenant_id, created_at DESC);

CREATE TABLE marketplace_return (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    order_id        BIGINT       NOT NULL REFERENCES marketplace_order(id),
    external_return_id VARCHAR(128) NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    reason          TEXT         NULL,
    payload_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mp_return_status CHECK (status IN ('OPEN', 'APPROVED', 'REJECTED', 'RESTOCKED', 'CLOSED'))
);

CREATE TABLE marketplace_settlement (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    channel_id      BIGINT       NOT NULL REFERENCES marketplace_channel(id),
    external_settlement_id VARCHAR(128) NULL,
    period_start    DATE         NULL,
    period_end      DATE         NULL,
    amount          NUMERIC(18, 2) NULL,
    currency        VARCHAR(8)   NOT NULL DEFAULT 'INR',
    payload_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mp_settlement_channel ON marketplace_settlement (channel_id, period_start);
