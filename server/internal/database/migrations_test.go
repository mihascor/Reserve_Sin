package database

import (
	"context"
	"testing"
)

func TestApplyMigrationsCreatesInitialSchema(t *testing.T) {
	ctx := context.Background()
	db, err := Open(ctx, ":memory:")
	if err != nil {
		t.Fatalf("Open() error = %v", err)
	}
	t.Cleanup(func() { _ = db.Close() })

	if err := ApplyMigrations(ctx, db); err != nil {
		t.Fatalf("ApplyMigrations() error = %v", err)
	}
	if err := ApplyMigrations(ctx, db); err != nil {
		t.Fatalf("second ApplyMigrations() error = %v", err)
	}

	for _, table := range []string{"categories", "transaction_labels", "transactions", "app_state"} {
		var name string
		err := db.QueryRowContext(ctx, "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", table).Scan(&name)
		if err != nil {
			t.Errorf("table %q was not created: %v", table, err)
		}
	}

	var revision string
	if err := db.QueryRowContext(ctx, "SELECT value FROM app_state WHERE key = 'current_revision'").Scan(&revision); err != nil {
		t.Fatalf("read current revision: %v", err)
	}
	if revision != "0" {
		t.Errorf("current revision = %q, want 0", revision)
	}
}

func TestInitialSchemaRejectsInvalidTransaction(t *testing.T) {
	ctx := context.Background()
	db, err := Open(ctx, ":memory:")
	if err != nil {
		t.Fatalf("Open() error = %v", err)
	}
	t.Cleanup(func() { _ = db.Close() })
	if err := ApplyMigrations(ctx, db); err != nil {
		t.Fatalf("ApplyMigrations() error = %v", err)
	}

	if _, err = db.ExecContext(ctx, `
		INSERT INTO transactions (id, category_id, amount_rub, occurred_at, created_at, updated_at, client_operation_id)
		VALUES ('transaction-1', 'missing-category', 0, '2026-08-05T00:00:00Z', '2026-08-05T00:00:00Z', '2026-08-05T00:00:00Z', 'client-1')`); err == nil {
		t.Fatal("invalid transaction was accepted")
	}

	if _, err = db.ExecContext(ctx, `
		INSERT INTO transactions (id, category_id, amount_rub, occurred_at, created_at, updated_at, client_operation_id)
		VALUES ('transaction-2', 'missing-category', 1, '2026-08-05T00:00:00Z', '2026-08-05T00:00:00Z', '2026-08-05T00:00:00Z', 'client-2')`); err == nil {
		t.Fatal("transaction with a missing category was accepted")
	}

	if _, err = db.ExecContext(ctx, `
		INSERT INTO categories (id, name, sort_order, created_at, updated_at)
		VALUES ('category-1', 'Подушка', 0, '2026-08-05T00:00:00Z', '2026-08-05T00:00:00Z')`); err != nil {
		t.Fatalf("insert category: %v", err)
	}
	if _, err = db.ExecContext(ctx, `
		INSERT INTO transactions (id, category_id, amount_rub, occurred_at, created_at, updated_at, client_operation_id)
		VALUES ('transaction-3', 'category-1', 100, '2026-08-05T00:00:00Z', '2026-08-05T00:00:00Z', '2026-08-05T00:00:00Z', 'client-3')`); err != nil {
		t.Fatalf("insert valid transaction: %v", err)
	}
	if _, err = db.ExecContext(ctx, `
		INSERT INTO transactions (id, category_id, amount_rub, occurred_at, created_at, updated_at, client_operation_id)
		VALUES ('transaction-4', 'category-1', 100, '2026-08-05T00:00:00Z', '2026-08-05T00:00:00Z', '2026-08-05T00:00:00Z', 'client-3')`); err == nil {
		t.Fatal("duplicate client_operation_id was accepted")
	}
}
