CREATE TABLE categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    currency TEXT NOT NULL DEFAULT 'RUB' CHECK (currency = 'RUB'),
    target_amount_rub INTEGER CHECK (target_amount_rub IS NULL OR target_amount_rub >= 0),
    sort_order INTEGER NOT NULL,
    is_archived INTEGER NOT NULL DEFAULT 0 CHECK (is_archived IN (0, 1)),
    is_visible_on_home INTEGER NOT NULL DEFAULT 1 CHECK (is_visible_on_home IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0)
);

CREATE TABLE transaction_labels (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    sort_order INTEGER NOT NULL,
    is_archived INTEGER NOT NULL DEFAULT 0 CHECK (is_archived IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0)
);

CREATE TABLE transactions (
    id TEXT PRIMARY KEY,
    category_id TEXT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    label_id TEXT REFERENCES transaction_labels(id) ON DELETE RESTRICT,
    batch_id TEXT,
    amount_rub INTEGER NOT NULL CHECK (amount_rub <> 0),
    comment TEXT,
    occurred_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    client_operation_id TEXT NOT NULL UNIQUE CHECK (length(trim(client_operation_id)) > 0),
    is_cancelled INTEGER NOT NULL DEFAULT 0 CHECK (is_cancelled IN (0, 1)),
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0)
);

CREATE TABLE app_state (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO app_state (key, value) VALUES ('current_revision', '0');

CREATE INDEX categories_active_sort_order_idx ON categories(is_archived, sort_order, id);
CREATE INDEX transaction_labels_active_sort_order_idx ON transaction_labels(is_archived, sort_order, id);
CREATE INDEX transactions_occurred_at_idx ON transactions(occurred_at DESC, id DESC);
CREATE INDEX transactions_category_occurred_at_idx ON transactions(category_id, occurred_at DESC, id DESC);
CREATE INDEX transactions_label_occurred_at_idx ON transactions(label_id, occurred_at DESC, id DESC);
CREATE INDEX transactions_batch_id_idx ON transactions(batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX transactions_revision_idx ON transactions(revision, id);
CREATE INDEX categories_revision_idx ON categories(revision, id);
CREATE INDEX transaction_labels_revision_idx ON transaction_labels(revision, id);
