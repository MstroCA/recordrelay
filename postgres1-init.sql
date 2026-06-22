-- ============================================================
-- RecordRelay Test Source DB — postgres1
-- Full SaaS + e-commerce schema with seed data
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

-- ============================================================
-- SEED DATA
-- ============================================================

-- Product categories
INSERT INTO product_categories (name, slug) VALUES
    ('Electronics',      'electronics'),
    ('Computers',        'computers'),
    ('Peripherals',      'peripherals'),
    ('Mobile',           'mobile'),
    ('Office Supplies',  'office-supplies'),
    ('Software',         'software');

INSERT INTO product_categories (name, slug, parent_id) VALUES
    ('Laptops',          'laptops',   2),
    ('Desktops',         'desktops',  2),
    ('Keyboards',        'keyboards', 3),
    ('Monitors',         'monitors',  3),
    ('Mice',             'mice',      3),
    ('Headphones',       'headphones',1);

-- Coupons
INSERT INTO coupons (code, discount_pct, valid_until, max_uses) VALUES
    ('WELCOME10',  10.00, '2027-12-31 23:59:59', 1000),
    ('SUMMER20',   20.00, '2026-09-01 00:00:00',  500),
    ('VIP30',      30.00, '2027-06-30 23:59:59',  100);

INSERT INTO coupons (code, discount_fixed, valid_until, max_uses) VALUES
    ('FLAT50',  50.00,  '2026-12-31 23:59:59', 200),
    ('FLAT100', 100.00, '2026-12-31 23:59:59',  50);

-- Warehouses
INSERT INTO warehouses (name, address, city, country) VALUES
    ('Istanbul Main',    'Atatürk Cad. No:1',      'Istanbul',  'TR'),
    ('Ankara Hub',       'Cumhuriyet Blv. No:42',   'Ankara',    'TR'),
    ('Izmir Depot',      'Alsancak Mah. No:7',      'Izmir',     'TR');

-- Accounts
INSERT INTO accounts (name, plan, owner_email, iban, tax_number, national_id, phone) VALUES
    ('Acme Corp',        'enterprise', 'cfo@acme.example.com',      'TR330006100519786457841326', '1234567890', '12345678901', '+905551112233'),
    ('Startup Hub',      'pro',        'founder@startuphub.example', 'TR610006100519786457841001', '9876543210', '98765432109', '+905559998877'),
    ('FreelancerCo',     'free',       'me@freelancer.example',      NULL,                         NULL,         '55544433322', '+905553334455');

-- Users
INSERT INTO users (account_id, email, full_name, phone_number, role, password_hash, national_id) VALUES
    (1, 'ali.kaya@acme.example.com',      'Ali Kaya',      '+905551234567', 'admin',  '$2b$12$hash1', '11111111111'),
    (1, 'ayse.demir@acme.example.com',    'Ayşe Demir',    '+905552345678', 'member', '$2b$12$hash2', '22222222222'),
    (1, 'mehmet.yilmaz@acme.example.com', 'Mehmet Yılmaz', '+905553456789', 'member', '$2b$12$hash3', '33333333333'),
    (2, 'ece.sahin@startuphub.example',   'Ece Şahin',     '+905554567890', 'admin',  '$2b$12$hash4', '44444444444'),
    (2, 'can.ozturk@startuphub.example',  'Can Öztürk',    '+905555678901', 'member', '$2b$12$hash5', '55555555555'),
    (3, 'solo@freelancer.example',        'Selin Önder',   '+905556789012', 'admin',  '$2b$12$hash6', '66666666666');

-- Sessions
INSERT INTO sessions (user_id, token, ip_address, expires_at) VALUES
    (1, 'tok_aaa111bbb222ccc333',  '192.168.1.10', NOW() + INTERVAL '7 days'),
    (2, 'tok_ddd444eee555fff666',  '192.168.1.11', NOW() + INTERVAL '7 days'),
    (4, 'tok_ggg777hhh888iii999',  '10.0.0.42',    NOW() + INTERVAL '1 day');

-- Audit logs
INSERT INTO audit_logs (user_id, action, entity_type, entity_id, payload, ip_address) VALUES
    (1, 'user.login',       'user',     1, '{"method":"password"}',         '192.168.1.10'),
    (1, 'order.created',    'order',    1, '{"total":4599.00}',              '192.168.1.10'),
    (2, 'product.updated',  'product',  3, '{"field":"base_price"}',         '192.168.1.11'),
    (4, 'ticket.created',   'ticket',   1, '{"subject":"Wrong item sent"}',  '10.0.0.42'),
    (1, 'invoice.paid',     'invoice',  1, '{"amount":4599.00}',             '192.168.1.10');

-- Notifications
INSERT INTO notifications (user_id, type, title, body) VALUES
    (1, 'order.shipped',    'Siparişiniz Kargoya Verildi',  'Sipariş #1 kargoya verildi. Takip: TR1234567890'),
    (2, 'ticket.reply',     'Destek Talebi Yanıtlandı',     '#1 numaralı destek talebinize yanıt geldi.'),
    (4, 'invoice.due',      'Fatura Ödeme Tarihi Yaklaşıyor', '#INV-2026-001 faturanız 3 gün içinde vadesi doluyor.'),
    (1, 'promo.new',        'Yeni Kampanya!',               'SUMMER20 kupon koduyla %20 indirim kazanın.');

-- Customers
INSERT INTO customers (account_manager_id, email, first_name, last_name, phone, phone_number, national_id, iban, date_of_birth, segment) VALUES
    (1, 'burak.arslan@example.com',   'Burak',   'Arslan',   '+905551110001', '+905551110001', '10000000001', 'TR330006100519786457841326', '1988-03-15', 'premium'),
    (1, 'fatma.celik@example.com',    'Fatma',   'Çelik',    '+905551110002', '+905551110002', '10000000002', 'TR610006100519786457841002', '1992-07-22', 'standard'),
    (2, 'kemal.aydin@example.com',    'Kemal',   'Aydın',    '+905551110003', '+905551110003', '10000000003', 'TR270006100519786457841003', '1985-11-05', 'premium'),
    (4, 'neslihan.koc@example.com',   'Neslihan','Koç',      '+905551110004', '+905551110004', '10000000004', 'TR940006100519786457841004', '1995-01-30', 'standard'),
    (6, 'onur.aksoy@example.com',     'Onur',    'Aksoy',    '+905551110005', '+905551110005', '10000000005', NULL,                         '1990-09-18', 'vip');

-- Addresses
INSERT INTO addresses (customer_id, label, street_address, address, city, postal_code, country, is_default) VALUES
    (1, 'home',    'Bağcılar Mah. Lale Sok. No:12',  'Bağcılar Mah. Lale Sok. No:12 D:5 Kadıköy', 'Istanbul', '34710', 'TR', TRUE),
    (1, 'work',    'Maslak Büyükdere Cad. No:255',   'Maslak Büyükdere Cad. No:255 Sarıyer',       'Istanbul', '34398', 'TR', FALSE),
    (2, 'home',    'Kızılay Atatürk Blv. No:72',     'Kızılay Atatürk Blv. No:72 Çankaya',         'Ankara',   '06420', 'TR', TRUE),
    (3, 'home',    'Konak Fevzipaşa Blv. No:88',     'Konak Fevzipaşa Blv. No:88 Konak',           'Izmir',    '35250', 'TR', TRUE),
    (4, 'home',    'Alsancak Cumhuriyet Blv. No:10', 'Alsancak Cumhuriyet Blv. No:10 Konak',        'Izmir',    '35220', 'TR', TRUE),
    (5, 'home',    'Çeliktepe Mah. Gül Sok. No:3',   'Çeliktepe Mah. Gül Sok. No:3 D:2 Kâğıthane', 'Istanbul', '34413', 'TR', TRUE);

-- Payment methods
INSERT INTO payment_methods (customer_id, type, provider, last_four, is_default) VALUES
    (1, 'credit_card', 'Mastercard', '4242', TRUE),
    (1, 'credit_card', 'Visa',       '1111', FALSE),
    (2, 'credit_card', 'Visa',       '8888', TRUE),
    (3, 'credit_card', 'Amex',       '0005', TRUE),
    (4, 'credit_card', 'Mastercard', '3232', TRUE),
    (5, 'bank_transfer', NULL,       NULL,   TRUE);

INSERT INTO payment_methods (customer_id, type, provider, iban, is_default) VALUES
    (1, 'bank_transfer', 'Ziraat', 'TR330006100519786457841326', FALSE),
    (3, 'bank_transfer', 'İşbank', 'TR270006100519786457841003', FALSE);

-- Products
INSERT INTO products (category_id, name, slug, description, base_price, sku) VALUES
    (7,  'ProBook 15 Laptop',        'probook-15',        'Yüksek performanslı 15" iş laptopu',           24999.00, 'LPT-PROBOOK-15'),
    (7,  'UltraSlim 13 Laptop',      'ultraslim-13',      'Hafif ve ince 13" ultrabook',                  19999.00, 'LPT-ULTRA-13'),
    (10, 'ViewMax 27" Monitor',       'viewmax-27',        '4K IPS panel 27 inç monitör',                   8499.00, 'MON-VMAX-27'),
    (10, 'ViewMax 24" Monitor',       'viewmax-24',        'Full HD IPS 24 inç monitör',                    4299.00, 'MON-VMAX-24'),
    (9,  'MechaType Pro Keyboard',    'mechatype-pro',     'Mekanik gaming/ofis klavyesi RGB',              1899.00, 'KEY-MECHA-PRO'),
    (9,  'SlimType Wireless',         'slimtype-wireless', 'Kablosuz ince klavye',                           699.00, 'KEY-SLIM-WL'),
    (11, 'ErgoMove Pro Mouse',        'ergomove-pro',      'Ergonomik kablosuz mouse',                       799.00, 'MSE-ERGO-PRO'),
    (12, 'SoundWave ANC Headphones',  'soundwave-anc',     'Aktif gürültü önleyici kulaklık',              3299.00, 'HPH-SW-ANC');

-- Product variants
INSERT INTO product_variants (product_id, name, sku, price_delta, stock_total, attributes) VALUES
    -- ProBook 15: RAM/storage combos
    (1, '16GB / 512GB SSD',  'LPT-PROBOOK-15-16-512',  0,      25, '{"ram":"16GB","storage":"512GB"}'),
    (1, '32GB / 1TB SSD',    'LPT-PROBOOK-15-32-1T',   5000,   10, '{"ram":"32GB","storage":"1TB"}'),
    -- UltraSlim 13
    (2, '8GB / 256GB SSD',   'LPT-ULTRA-13-8-256',     0,      40, '{"ram":"8GB","storage":"256GB"}'),
    (2, '16GB / 512GB SSD',  'LPT-ULTRA-13-16-512',    3000,   20, '{"ram":"16GB","storage":"512GB"}'),
    -- ViewMax 27
    (3, 'Black',             'MON-VMAX-27-BLK',        0,      30, '{"color":"Black"}'),
    (3, 'White',             'MON-VMAX-27-WHT',        200,    15, '{"color":"White"}'),
    -- ViewMax 24
    (4, 'Black',             'MON-VMAX-24-BLK',        0,      50, '{"color":"Black"}'),
    -- MechaType Pro
    (5, 'Cherry MX Red',     'KEY-MECHA-RED',          0,      60, '{"switch":"Cherry MX Red"}'),
    (5, 'Cherry MX Blue',    'KEY-MECHA-BLU',          100,    45, '{"switch":"Cherry MX Blue"}'),
    -- SlimType Wireless
    (6, 'Space Grey',        'KEY-SLIM-WL-SGR',        0,      80, '{"color":"Space Grey"}'),
    (6, 'White',             'KEY-SLIM-WL-WHT',        0,      75, '{"color":"White"}'),
    -- ErgoMove Pro
    (7, 'Black',             'MSE-ERGO-PRO-BLK',       0,      90, '{"color":"Black"}'),
    -- SoundWave ANC
    (8, 'Midnight Black',    'HPH-SW-ANC-BLK',         0,      35, '{"color":"Midnight Black"}'),
    (8, 'Pearl White',       'HPH-SW-ANC-WHT',         300,    20, '{"color":"Pearl White"}');

-- Inventory
INSERT INTO inventory (variant_id, warehouse_id, quantity, reserved) VALUES
    (1,  1, 15, 2), (1,  2, 10, 1),
    (2,  1,  8, 0), (2,  2,  2, 0),
    (3,  1, 20, 3), (3,  3, 20, 1),
    (4,  1, 12, 1), (4,  2,  8, 0),
    (5,  1, 18, 2), (5,  2, 12, 1),
    (6,  1, 10, 0),
    (7,  1, 30, 5), (7,  3, 20, 2),
    (8,  1, 35, 3), (8,  2, 25, 2),
    (9,  1, 25, 1), (9,  2, 20, 1),
    (10, 1, 50, 4), (10, 2, 30, 2),
    (11, 1, 40, 2),
    (12, 1, 60, 5), (12, 2, 30, 3),
    (13, 1, 20, 1), (13, 2, 15, 1),
    (14, 1, 12, 0), (14, 2,  8, 0);

-- Subscriptions
INSERT INTO subscriptions (account_id, owner_id, plan, status, billing_cycle, price) VALUES
    (1, 1, 'enterprise', 'active',    'yearly',  59988.00),
    (2, 4, 'pro',        'active',    'monthly',  2999.00),
    (3, 6, 'free',       'active',    'monthly',     0.00);

INSERT INTO subscription_items (subscription_id, product_id, quantity, unit_price) VALUES
    (1, 6, 10,  699.00),
    (1, 7, 10,  799.00),
    (2, 6,  3,  699.00),
    (2, 7,  3,  799.00);

-- Orders
INSERT INTO orders (customer_id, created_by, shipping_address_id, coupon_id, status, subtotal, discount_amount, shipping_fee, tax_amount, total_amount) VALUES
    (1, 1, 1, 1, 'delivered',  24999.00, 2499.90,   0.00, 4049.84, 26548.94),
    (1, 1, 2, NULL,'shipped',   8499.00,    0.00,   0.00, 1529.82,  9028.82 + 0.00),
    (2, 2, 3, 2, 'delivered',   6198.00, 1239.60,  49.90,  900.95,  5909.25),
    (3, 1, 4, NULL,'processing',29999.00,    0.00,   0.00, 5399.82, 35398.82),
    (4, 4, 5, 3, 'pending',     4998.00, 1499.40,  49.90,  629.82,  4178.32),
    (5, 6, 6, NULL,'delivered',  3299.00,    0.00,   0.00,  593.82,  3892.82),
    (1, 1, 1, 4, 'cancelled',   1898.00,   50.00,  49.90,  323.64,  2221.54),
    (3, 2, 4, NULL,'delivered',  3598.00,    0.00,   0.00,  647.64,  4245.64);

-- Order items
INSERT INTO order_items (order_id, variant_id, quantity, unit_price, total_price) VALUES
    (1, 1,  1, 24999.00, 24999.00),
    (2, 5,  1,  8499.00,  8499.00),
    (3, 10, 1,   699.00,   699.00),
    (3, 12, 1,   799.00,   799.00),
    (3, 5,  1,  8499.00,  8499.00 - 8499.00 + 4699.00),
    (4, 2,  1, 29999.00, 29999.00),
    (5, 8,  1,  1899.00,  1899.00),
    (5, 12, 1,   799.00,   799.00),
    (5, 13, 2,  3299.00,  6598.00 - 4299.00),   -- 2x headphones discounted
    (6, 13, 1,  3299.00,  3299.00),
    (7, 8,  1,  1899.00,  1899.00),
    (8, 7,  1,  4299.00,  4299.00 - 701.00);

-- Order coupons (where coupon was applied)
INSERT INTO order_coupons (order_id, coupon_id, discount) VALUES
    (1, 1, 2499.90),
    (3, 2, 1239.60),
    (5, 3, 1499.40),
    (7, 4,   50.00);

-- Shipments
INSERT INTO shipments (order_id, warehouse_id, carrier, tracking_number, status, shipped_at, delivered_at, estimated_at) VALUES
    (1, 1, 'Aras Kargo',   'ARAS1000000001', 'delivered', '2026-01-10 09:00:00', '2026-01-12 14:30:00', '2026-01-13 18:00:00'),
    (2, 1, 'Yurtiçi Kargo','YURTICI2000001', 'shipped',   '2026-06-15 10:00:00', NULL,                  '2026-06-18 18:00:00'),
    (3, 2, 'MNG Kargo',    'MNG3000000001',  'delivered', '2026-02-01 11:00:00', '2026-02-03 16:00:00', '2026-02-04 18:00:00'),
    (6, 1, 'Aras Kargo',   'ARAS4000000001', 'delivered', '2026-03-05 08:30:00', '2026-03-07 13:00:00', '2026-03-08 18:00:00'),
    (8, 3, 'Yurtiçi Kargo','YURTICI5000001', 'delivered', '2026-04-10 09:00:00', '2026-04-12 15:00:00', '2026-04-13 18:00:00');

-- Shipment items
INSERT INTO shipment_items (shipment_id, order_item_id, quantity) VALUES
    (1, 1,  1),
    (2, 2,  1),
    (3, 3,  1),
    (3, 4,  1),
    (4, 10, 1),
    (5, 12, 1);

-- Returns
INSERT INTO returns (order_id, reason, status, refund_amount, requested_at, resolved_at) VALUES
    (3, 'Yanlış ürün gönderildi', 'refunded', 699.00, '2026-02-05 10:00:00', '2026-02-08 15:00:00');

INSERT INTO return_items (return_id, order_item_id, quantity, condition) VALUES
    (1, 3, 1, 'unopened');

-- Invoices
INSERT INTO invoices (order_id, customer_id, number, status, issued_at, due_at, paid_at, amount, tax_amount) VALUES
    (1, 1, 'INV-2026-001', 'paid',  '2026-01-09',  '2026-01-23',  '2026-01-09',  26548.94, 4049.84),
    (2, 1, 'INV-2026-002', 'sent',  '2026-06-14',  '2026-06-28',  NULL,           9028.82,  1529.82),
    (3, 2, 'INV-2026-003', 'paid',  '2026-02-01',  '2026-02-15',  '2026-02-03',   5909.25,   900.95),
    (4, 3, 'INV-2026-004', 'draft', '2026-06-20',  '2026-07-04',  NULL,          35398.82,  5399.82),
    (6, 5, 'INV-2026-005', 'paid',  '2026-03-04',  '2026-03-18',  '2026-03-06',   3892.82,   593.82),
    (8, 3, 'INV-2026-006', 'paid',  '2026-04-09',  '2026-04-23',  '2026-04-11',   4245.64,   647.64);

-- Invoice line items
INSERT INTO invoice_line_items (invoice_id, order_item_id, description, quantity, unit_price, total_price) VALUES
    (1, 1,  'ProBook 15 Laptop (16GB/512GB)',  1, 24999.00, 24999.00),
    (2, 2,  'ViewMax 27" Monitor (Black)',     1,  8499.00,  8499.00),
    (3, 3,  'SlimType Wireless (Space Grey)',  1,   699.00,   699.00),
    (3, 4,  'ErgoMove Pro Mouse (Black)',      1,   799.00,   799.00),
    (4, 6,  'ProBook 15 Laptop (32GB/1TB)',    1, 29999.00, 29999.00),
    (5, 10, 'SoundWave ANC (Midnight Black)',  1,  3299.00,  3299.00),
    (6, 12, 'ViewMax 24" Monitor (Black)',     1,  4299.00,  4299.00);

-- Payments
INSERT INTO payments (invoice_id, payment_method_id, amount, status, provider, provider_tx_id, paid_at) VALUES
    (1, 1, 26548.94, 'succeeded', 'iyzico', 'iyz_txn_00000001', '2026-01-09 14:23:11'),
    (3, 3,  5909.25, 'succeeded', 'stripe', 'ch_00000001',       '2026-02-03 09:17:44'),
    (5, 6,  3892.82, 'succeeded', 'iyzico', 'iyz_txn_00000002', '2026-03-06 11:05:30'),
    (6, 4,  4245.64, 'succeeded', 'stripe', 'ch_00000002',       '2026-04-11 16:42:08'),
    (2, 1,  9028.82, 'pending',   'iyzico', NULL,                NULL);

-- Support tickets
INSERT INTO tickets (customer_id, order_id, assigned_to, subject, body, status, priority) VALUES
    (1, 1, 2, 'Laptop kutusu hasarlı geldi',   'Kargo sırasında kutunun köşesi ezilmiş, ürün sorunsuz görünüyor ama kontrol etmek istiyorum.', 'resolved', 'high'),
    (2, 3, 2, 'Sipariş eksik ürünle geldi',    'Klavye ve mouse ayrı paketlendi, mouse paketi içinde değildi.', 'resolved', 'normal'),
    (4, 5, 1, 'İptal etmek istiyorum',         'Siparişi henüz onaylandı, kargoya verilmeden iptal mümkün mü?', 'open', 'normal'),
    (3, 4, 1, 'Fatura bilgisi güncelleme',     'Şirket adına fatura istiyorum, vergi numaramı ekleyebilir misiniz?', 'open', 'low');

-- Ticket comments
INSERT INTO ticket_comments (ticket_id, author_id, body, internal) VALUES
    (1, 2,    'Merhaba, üzgünüz. Fotoğraf paylaşabilir misiniz? Hemen işlem başlatacağız.',              FALSE),
    (1, NULL, 'Fotoğrafları e-posta ile gönderdim.',                                                     FALSE),
    (1, 2,    'Fotoğrafları inceledik, ürün sorunsuz görünüyor. Sorun yaşarsanız hemen bildirin.',       FALSE),
    (1, 1,    'Müşteri memnun, kapatıldı.',                                                              TRUE),
    (2, 2,    'Araştırdım, mouse paketle birlikte çıktı, kargoda kaybolmuş olabilir. Yenisini göndereceğiz.', FALSE),
    (2, NULL, 'Teşekkürler, yeni ürün geldi.',                                                           FALSE),
    (3, 1,    'Sipariş işlemde, 24 saat içinde kargoya verilecek. İptal için destek hattını arayın.',  FALSE),
    (4, 1,    'Fatura departmanına ilettik, 1 iş günü içinde güncellenir.',                             FALSE);
