package importer

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/csv"
	"encoding/hex"
	"fmt"
	"io"
	"strconv"
	"strings"
	"time"
)

var ignoredCategories = map[string]bool{
	"Пусто 1": true,
	"Пусто 2": true,
	"Пусто 3": true,
}

type Result struct {
	CategoriesCreated   int
	LabelsCreated       int
	TransactionsCreated int
	TransactionsSkipped int
}

type csvOperation struct {
	line      int
	date      string
	labelName string
	amounts   []csvAmount
}

type csvAmount struct {
	categoryName string
	amountRub    int64
}

// ImportCSV imports the known financial-movement CSV format. sourceID namespaces
// client operation IDs, so repeating an already completed import is safe.
func ImportCSV(ctx context.Context, db *sql.DB, source io.Reader, sourceID string) (Result, error) {
	if strings.TrimSpace(sourceID) == "" {
		return Result{}, fmt.Errorf("source ID is required")
	}
	operations, err := parseCSV(source)
	if err != nil {
		return Result{}, err
	}

	tx, err := db.BeginTx(ctx, nil)
	if err != nil {
		return Result{}, fmt.Errorf("begin import transaction: %w", err)
	}
	defer tx.Rollback()

	result := Result{}
	categories := map[string]string{}
	labels := map[string]string{}
	for _, operation := range operations {
		for _, amount := range operation.amounts {
			if _, ok := categories[amount.categoryName]; !ok {
				id, created, err := ensureCategory(ctx, tx, amount.categoryName)
				if err != nil {
					return Result{}, err
				}
				categories[amount.categoryName] = id
				if created {
					result.CategoriesCreated++
				}
			}
		}
		if operation.labelName != "" {
			if _, ok := labels[operation.labelName]; !ok {
				id, created, err := ensureLabel(ctx, tx, operation.labelName)
				if err != nil {
					return Result{}, err
				}
				labels[operation.labelName] = id
				if created {
					result.LabelsCreated++
				}
			}
		}

		existing, err := existingCount(ctx, tx, sourceID, operation)
		if err != nil {
			return Result{}, err
		}
		if existing == len(operation.amounts) {
			result.TransactionsSkipped += existing
			continue
		}
		if existing != 0 {
			return Result{}, fmt.Errorf("CSV line %d was only partially imported", operation.line)
		}

		var batchID *string
		if len(operation.amounts) > 1 {
			id, err := newID()
			if err != nil {
				return Result{}, err
			}
			batchID = &id
		}
		var labelID *string
		if operation.labelName != "" {
			id := labels[operation.labelName]
			labelID = &id
		}
		for _, amount := range operation.amounts {
			id, err := newID()
			if err != nil {
				return Result{}, err
			}
			revision, err := nextRevision(ctx, tx)
			if err != nil {
				return Result{}, err
			}
			timestamp := time.Now().UTC().Format(time.RFC3339Nano)
			clientOperationID := fmt.Sprintf("csv:%s:%d:%s", sourceID, operation.line, amount.categoryName)
			if _, err := tx.ExecContext(ctx, `INSERT INTO transactions
				(id, category_id, label_id, batch_id, amount_rub, comment, occurred_at, created_at, updated_at, client_operation_id, revision)
				VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?)`,
				id, categories[amount.categoryName], labelID, batchID, amount.amountRub, operation.date, timestamp, timestamp, clientOperationID, revision,
			); err != nil {
				return Result{}, fmt.Errorf("insert CSV line %d: %w", operation.line, err)
			}
			result.TransactionsCreated++
		}
	}
	if err := tx.Commit(); err != nil {
		return Result{}, fmt.Errorf("commit CSV import: %w", err)
	}
	return result, nil
}

func parseCSV(source io.Reader) ([]csvOperation, error) {
	reader := csv.NewReader(source)
	reader.FieldsPerRecord = -1
	records, err := reader.ReadAll()
	if err != nil {
		return nil, fmt.Errorf("read CSV: %w", err)
	}
	if len(records) < 3 || len(records[1]) < 3 || strings.TrimSpace(records[1][0]) != "Дата" {
		return nil, fmt.Errorf("CSV does not contain the expected header row")
	}

	categoryColumns := map[int]string{}
	for index, name := range records[1] {
		name = strings.TrimSpace(name)
		if index >= 2 && name != "" && !ignoredCategories[name] {
			categoryColumns[index] = name
		}
	}
	if len(categoryColumns) == 0 {
		return nil, fmt.Errorf("CSV does not contain categories")
	}

	operations := make([]csvOperation, 0)
	for index, record := range records[2:] {
		line := index + 3
		if len(record) == 0 || strings.TrimSpace(record[0]) == "" {
			continue
		}
		parsedDate, err := time.Parse("02.01.2006", strings.TrimSpace(record[0]))
		if err != nil {
			return nil, fmt.Errorf("CSV line %d: invalid date: %w", line, err)
		}
		operation := csvOperation{line: line, date: parsedDate.UTC().Format(time.RFC3339)}
		if len(record) > 1 {
			label := strings.TrimSpace(record[1])
			if label != "---" {
				operation.labelName = label
			}
		}
		for column, categoryName := range categoryColumns {
			if column >= len(record) || strings.TrimSpace(record[column]) == "" {
				continue
			}
			amount, err := parseRub(record[column])
			if err != nil {
				return nil, fmt.Errorf("CSV line %d, category %q: %w", line, categoryName, err)
			}
			if amount == 0 {
				return nil, fmt.Errorf("CSV line %d, category %q: amount must not be zero", line, categoryName)
			}
			operation.amounts = append(operation.amounts, csvAmount{categoryName: categoryName, amountRub: amount})
		}
		if len(operation.amounts) > 0 {
			operations = append(operations, operation)
		}
	}
	return operations, nil
}

func parseRub(value string) (int64, error) {
	cleaned := strings.NewReplacer("\u00a0", "", " ", "", "₽", "", "р.", "").Replace(strings.TrimSpace(value))
	amount, err := strconv.ParseInt(cleaned, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("invalid ruble amount %q", value)
	}
	return amount, nil
}

func ensureCategory(ctx context.Context, tx *sql.Tx, name string) (string, bool, error) {
	var id string
	err := tx.QueryRowContext(ctx, "SELECT id FROM categories WHERE name = ? ORDER BY id LIMIT 1", name).Scan(&id)
	if err == nil {
		return id, false, nil
	}
	if err != sql.ErrNoRows {
		return "", false, fmt.Errorf("find category %q: %w", name, err)
	}
	id, err = newID()
	if err != nil {
		return "", false, err
	}
	var lastOrder sql.NullInt64
	if err := tx.QueryRowContext(ctx, "SELECT MAX(sort_order) FROM categories").Scan(&lastOrder); err != nil {
		return "", false, fmt.Errorf("read category order: %w", err)
	}
	revision, err := nextRevision(ctx, tx)
	if err != nil {
		return "", false, err
	}
	timestamp := time.Now().UTC().Format(time.RFC3339Nano)
	order := int64(0)
	if lastOrder.Valid {
		order = lastOrder.Int64 + 1
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO categories
		(id, name, sort_order, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?)`,
		id, name, order, timestamp, timestamp, revision,
	); err != nil {
		return "", false, fmt.Errorf("create category %q: %w", name, err)
	}
	return id, true, nil
}

func ensureLabel(ctx context.Context, tx *sql.Tx, name string) (string, bool, error) {
	var id string
	err := tx.QueryRowContext(ctx, "SELECT id FROM transaction_labels WHERE name = ? ORDER BY id LIMIT 1", name).Scan(&id)
	if err == nil {
		return id, false, nil
	}
	if err != sql.ErrNoRows {
		return "", false, fmt.Errorf("find label %q: %w", name, err)
	}
	id, err = newID()
	if err != nil {
		return "", false, err
	}
	var lastOrder sql.NullInt64
	if err := tx.QueryRowContext(ctx, "SELECT MAX(sort_order) FROM transaction_labels").Scan(&lastOrder); err != nil {
		return "", false, fmt.Errorf("read label order: %w", err)
	}
	revision, err := nextRevision(ctx, tx)
	if err != nil {
		return "", false, err
	}
	timestamp := time.Now().UTC().Format(time.RFC3339Nano)
	order := int64(0)
	if lastOrder.Valid {
		order = lastOrder.Int64 + 1
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO transaction_labels
		(id, name, sort_order, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?)`,
		id, name, order, timestamp, timestamp, revision,
	); err != nil {
		return "", false, fmt.Errorf("create label %q: %w", name, err)
	}
	return id, true, nil
}

func existingCount(ctx context.Context, tx *sql.Tx, sourceID string, operation csvOperation) (int, error) {
	count := 0
	for _, amount := range operation.amounts {
		clientOperationID := fmt.Sprintf("csv:%s:%d:%s", sourceID, operation.line, amount.categoryName)
		var exists bool
		if err := tx.QueryRowContext(ctx, "SELECT EXISTS(SELECT 1 FROM transactions WHERE client_operation_id = ?)", clientOperationID).Scan(&exists); err != nil {
			return 0, fmt.Errorf("check CSV line %d: %w", operation.line, err)
		}
		if exists {
			count++
		}
	}
	return count, nil
}

func nextRevision(ctx context.Context, tx *sql.Tx) (int64, error) {
	var value string
	if err := tx.QueryRowContext(ctx, "SELECT value FROM app_state WHERE key = 'current_revision'").Scan(&value); err != nil {
		return 0, fmt.Errorf("read revision: %w", err)
	}
	revision, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("parse revision: %w", err)
	}
	revision++
	if _, err := tx.ExecContext(ctx, "UPDATE app_state SET value = ? WHERE key = 'current_revision'", strconv.FormatInt(revision, 10)); err != nil {
		return 0, fmt.Errorf("update revision: %w", err)
	}
	return revision, nil
}

func newID() (string, error) {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return "", fmt.Errorf("generate ID: %w", err)
	}
	return hex.EncodeToString(bytes), nil
}
