CREATE TABLE wallets (
    id              UUID            PRIMARY KEY,
    owner_id        UUID            NOT NULL UNIQUE,
    balance_amount  NUMERIC(19, 4)  NOT NULL CHECK (balance_amount >= 0),
    currency        VARCHAR(3)      NOT NULL,
    version         BIGINT          NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL
);

CREATE TABLE transfers (
    id                  UUID            PRIMARY KEY,
    source_wallet_id    UUID            NOT NULL REFERENCES wallets (id),
    destination_wallet_id UUID          NOT NULL REFERENCES wallets (id),
    amount              NUMERIC(19, 4)  NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3)      NOT NULL,
    status              VARCHAR(32)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL,
    completed_at        TIMESTAMPTZ,
    CONSTRAINT chk_transfer_distinct_wallets CHECK (source_wallet_id <> destination_wallet_id)
);

CREATE INDEX idx_transfers_source ON transfers (source_wallet_id);
CREATE INDEX idx_transfers_destination ON transfers (destination_wallet_id);
CREATE INDEX idx_transfers_created_at ON transfers (created_at DESC);
