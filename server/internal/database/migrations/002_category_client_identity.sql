ALTER TABLE categories ADD COLUMN client_category_id TEXT;

CREATE UNIQUE INDEX categories_client_category_id_idx
    ON categories(client_category_id)
    WHERE client_category_id IS NOT NULL;
