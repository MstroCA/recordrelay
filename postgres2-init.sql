-- ============================================================
-- RecordRelay Test Target DB — postgres2
-- Full SaaS + e-commerce schema — no seed data
-- ============================================================

-- ── Lookup / root tables ──────────────────────────────────

CREATE TABLE product_categories (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    parent_id   INTEGER REFERENCES product_categories(id),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE coupons (
    id              SERIAL PRIMARY KEY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    discount_pct    NUMERIC(5,2),
    discount_fixed  NUMERIC(10,2),
    valid_until     TIMESTAMP,
    max_uses        INTEGER,
    uses_so_far     INTEGER NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE warehouses (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    address     TEXT,
    city        VARCHAR(100),
    country     CHAR(2) NOT NULL DEFAULT 'TR',
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

-- ── Account / user hierarchy ──────────────────────────────

CREATE TABLE accounts (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    plan            VARCHAR(50)  NOT NULL DEFAULT 'free',
    owner_email     VARCHAR(255) NOT NULL UNIQUE,
    iban            VARCHAR(34),
    tax_number      VARCHAR(20),
    national_id     VARCHAR(20),
    phone           VARCHAR(30),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at    TIMESTAMP
);

CREATE TABLE users (
    id              SERIAL PRIMARY KEY,
    account_id      INTEGER NOT NULL REFERENCES accounts(id),
    email           VARCHAR(255) NOT NULL UNIQUE,
    full_name       VARCHAR(200),
    phone_number    VARCHAR(30),
    role            VARCHAR(50)  NOT NULL DEFAULT 'member',
    password_hash   VARCHAR(255),
    national_id     VARCHAR(20),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at   TIMESTAMP
);

CREATE TABLE sessions (
    id              SERIAL PRIMARY KEY,
    user_id         INTEGER NOT NULL REFERENCES users(id),
    token           VARCHAR(512) NOT NULL UNIQUE,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP NOT NULL,
    revoked_at      TIMESTAMP
);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     INTEGER REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   INTEGER,
    payload     JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    id          SERIAL PRIMARY KEY,
    user_id     INTEGER NOT NULL REFERENCES users(id),
    type        VARCHAR(100) NOT NULL,
    title       VARCHAR(255),
    body        TEXT,
    read_at     TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Customers (B2C) ───────────────────────────────────────

CREATE TABLE customers (
    id                  SERIAL PRIMARY KEY,
    account_manager_id  INTEGER REFERENCES users(id),
    email               VARCHAR(255) NOT NULL UNIQUE,
    first_name          VARCHAR(100),
    last_name           VARCHAR(100),
    phone               VARCHAR(30),
    phone_number        VARCHAR(30),
    national_id         VARCHAR(20),
    iban                VARCHAR(34),
    date_of_birth       DATE,
    segment             VARCHAR(50) NOT NULL DEFAULT 'standard',
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP
);

CREATE TABLE addresses (
    id              SERIAL PRIMARY KEY,
    customer_id     INTEGER NOT NULL REFERENCES customers(id),
    label           VARCHAR(50) NOT NULL DEFAULT 'home',
    street_address  VARCHAR(255),
    address         TEXT,
    city            VARCHAR(100),
    postal_code     VARCHAR(20),
    country         CHAR(2) NOT NULL DEFAULT 'TR',
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payment_methods (
    id              SERIAL PRIMARY KEY,
    customer_id     INTEGER NOT NULL REFERENCES customers(id),
    type            VARCHAR(50) NOT NULL,
    provider        VARCHAR(100),
    last_four       CHAR(4),
    iban            VARCHAR(34),
    expires_at      DATE,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Product catalogue ─────────────────────────────────────

CREATE TABLE products (
    id              SERIAL PRIMARY KEY,
    category_id     INTEGER REFERENCES product_categories(id),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    description     TEXT,
    base_price      NUMERIC(12,2) NOT NULL,
    currency        CHAR(3) NOT NULL DEFAULT 'TRY',
    sku             VARCHAR(100) UNIQUE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_variants (
    id              SERIAL PRIMARY KEY,
    product_id      INTEGER NOT NULL REFERENCES products(id),
    name            VARCHAR(100) NOT NULL,
    sku             VARCHAR(100) UNIQUE,
    price_delta     NUMERIC(12,2) NOT NULL DEFAULT 0,
    stock_total     INTEGER NOT NULL DEFAULT 0,
    attributes      JSONB,
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE inventory (
    id              SERIAL PRIMARY KEY,
    variant_id      INTEGER NOT NULL REFERENCES product_variants(id),
    warehouse_id    INTEGER NOT NULL REFERENCES warehouses(id),
    quantity        INTEGER NOT NULL DEFAULT 0,
    reserved        INTEGER NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (variant_id, warehouse_id)
);

-- ── Subscriptions ─────────────────────────────────────────

CREATE TABLE subscriptions (
    id              SERIAL PRIMARY KEY,
    account_id      INTEGER NOT NULL REFERENCES accounts(id),
    owner_id        INTEGER NOT NULL REFERENCES users(id),
    plan            VARCHAR(50) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'active',
    billing_cycle   VARCHAR(20) NOT NULL DEFAULT 'monthly',
    price           NUMERIC(12,2) NOT NULL,
    currency        CHAR(3) NOT NULL DEFAULT 'TRY',
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    renews_at       TIMESTAMP,
    cancelled_at    TIMESTAMP
);

CREATE TABLE subscription_items (
    id                  SERIAL PRIMARY KEY,
    subscription_id     INTEGER NOT NULL REFERENCES subscriptions(id),
    product_id          INTEGER NOT NULL REFERENCES products(id),
    quantity            INTEGER NOT NULL DEFAULT 1,
    unit_price          NUMERIC(12,2) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Orders ────────────────────────────────────────────────

CREATE TABLE orders (
    id                  SERIAL PRIMARY KEY,
    customer_id         INTEGER NOT NULL REFERENCES customers(id),
    created_by          INTEGER REFERENCES users(id),
    shipping_address_id INTEGER REFERENCES addresses(id),
    coupon_id           INTEGER REFERENCES coupons(id),
    status              VARCHAR(50) NOT NULL DEFAULT 'pending',
    subtotal            NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    shipping_fee        NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency            CHAR(3) NOT NULL DEFAULT 'TRY',
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    id              SERIAL PRIMARY KEY,
    order_id        INTEGER NOT NULL REFERENCES orders(id),
    variant_id      INTEGER NOT NULL REFERENCES product_variants(id),
    quantity        INTEGER NOT NULL,
    unit_price      NUMERIC(12,2) NOT NULL,
    total_price     NUMERIC(12,2) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_coupons (
    id          SERIAL PRIMARY KEY,
    order_id    INTEGER NOT NULL REFERENCES orders(id),
    coupon_id   INTEGER NOT NULL REFERENCES coupons(id),
    discount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    applied_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (order_id, coupon_id)
);

-- ── Shipments ─────────────────────────────────────────────

CREATE TABLE shipments (
    id              SERIAL PRIMARY KEY,
    order_id        INTEGER NOT NULL REFERENCES orders(id),
    warehouse_id    INTEGER REFERENCES warehouses(id),
    carrier         VARCHAR(100),
    tracking_number VARCHAR(200),
    status          VARCHAR(50) NOT NULL DEFAULT 'preparing',
    shipped_at      TIMESTAMP,
    delivered_at    TIMESTAMP,
    estimated_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shipment_items (
    id              SERIAL PRIMARY KEY,
    shipment_id     INTEGER NOT NULL REFERENCES shipments(id),
    order_item_id   INTEGER NOT NULL REFERENCES order_items(id),
    quantity        INTEGER NOT NULL
);

-- ── Returns ───────────────────────────────────────────────

CREATE TABLE returns (
    id              SERIAL PRIMARY KEY,
    order_id        INTEGER NOT NULL REFERENCES orders(id),
    reason          TEXT,
    status          VARCHAR(50) NOT NULL DEFAULT 'requested',
    refund_amount   NUMERIC(12,2),
    requested_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMP
);

CREATE TABLE return_items (
    id              SERIAL PRIMARY KEY,
    return_id       INTEGER NOT NULL REFERENCES returns(id),
    order_item_id   INTEGER NOT NULL REFERENCES order_items(id),
    quantity        INTEGER NOT NULL,
    condition       VARCHAR(50) NOT NULL DEFAULT 'good'
);

-- ── Invoices & Payments ───────────────────────────────────

CREATE TABLE invoices (
    id              SERIAL PRIMARY KEY,
    order_id        INTEGER NOT NULL REFERENCES orders(id),
    customer_id     INTEGER NOT NULL REFERENCES customers(id),
    number          VARCHAR(50) NOT NULL UNIQUE,
    status          VARCHAR(50) NOT NULL DEFAULT 'draft',
    issued_at       TIMESTAMP,
    due_at          TIMESTAMP,
    paid_at         TIMESTAMP,
    amount          NUMERIC(12,2) NOT NULL,
    tax_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency        CHAR(3) NOT NULL DEFAULT 'TRY',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoice_line_items (
    id              SERIAL PRIMARY KEY,
    invoice_id      INTEGER NOT NULL REFERENCES invoices(id),
    order_item_id   INTEGER REFERENCES order_items(id),
    description     VARCHAR(255) NOT NULL,
    quantity        INTEGER NOT NULL DEFAULT 1,
    unit_price      NUMERIC(12,2) NOT NULL,
    total_price     NUMERIC(12,2) NOT NULL
);

CREATE TABLE payments (
    id                  SERIAL PRIMARY KEY,
    invoice_id          INTEGER NOT NULL REFERENCES invoices(id),
    payment_method_id   INTEGER REFERENCES payment_methods(id),
    amount              NUMERIC(12,2) NOT NULL,
    currency            CHAR(3) NOT NULL DEFAULT 'TRY',
    status              VARCHAR(50) NOT NULL DEFAULT 'pending',
    provider            VARCHAR(100),
    provider_tx_id      VARCHAR(255),
    paid_at             TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Support Tickets ───────────────────────────────────────

CREATE TABLE tickets (
    id              SERIAL PRIMARY KEY,
    customer_id     INTEGER NOT NULL REFERENCES customers(id),
    order_id        INTEGER REFERENCES orders(id),
    assigned_to     INTEGER REFERENCES users(id),
    subject         VARCHAR(255) NOT NULL,
    body            TEXT,
    status          VARCHAR(50) NOT NULL DEFAULT 'open',
    priority        VARCHAR(20) NOT NULL DEFAULT 'normal',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMP
);

CREATE TABLE ticket_comments (
    id          SERIAL PRIMARY KEY,
    ticket_id   INTEGER NOT NULL REFERENCES tickets(id),
    author_id   INTEGER REFERENCES users(id),
    body        TEXT NOT NULL,
    internal    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
