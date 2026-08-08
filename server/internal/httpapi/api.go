package httpapi

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
)

const maxRequestBytes = 1 << 20

type errorBody struct {
	Error struct {
		Code    string         `json:"code"`
		Message string         `json:"message"`
		Details map[string]any `json:"details"`
	} `json:"error"`
}

type category struct {
	ID               string  `json:"id"`
	ClientCategoryID *string `json:"client_category_id"`
	Name             string  `json:"name"`
	Currency         string  `json:"currency"`
	TargetAmountRub  *int64  `json:"target_amount_rub"`
	SortOrder        int64   `json:"sort_order"`
	IsArchived       bool    `json:"is_archived"`
	IsVisibleOnHome  bool    `json:"is_visible_on_home"`
	CreatedAt        string  `json:"created_at"`
	UpdatedAt        string  `json:"updated_at"`
	Revision         int64   `json:"revision"`
}

type label struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	SortOrder  int64  `json:"sort_order"`
	IsArchived bool   `json:"is_archived"`
	CreatedAt  string `json:"created_at"`
	UpdatedAt  string `json:"updated_at"`
	Revision   int64  `json:"revision"`
}

type transaction struct {
	ID                string  `json:"id"`
	CategoryID        string  `json:"category_id"`
	LabelID           *string `json:"label_id"`
	BatchID           *string `json:"batch_id"`
	AmountRub         int64   `json:"amount_rub"`
	Comment           *string `json:"comment"`
	OccurredAt        string  `json:"occurred_at"`
	CreatedAt         string  `json:"created_at"`
	UpdatedAt         string  `json:"updated_at"`
	ClientOperationID string  `json:"client_operation_id"`
	IsCancelled       bool    `json:"is_cancelled"`
	Revision          int64   `json:"revision"`
}

// webHistoryTransaction is the deliberately small read model used by the
// protected web history. It is separate from transaction so Android's
// /transactions contract remains unchanged.
type webHistoryTransaction struct {
	ID           string  `json:"id"`
	OccurredAt   string  `json:"occurred_at"`
	CategoryName string  `json:"category_name"`
	LabelID      *string `json:"label_id"`
	LabelName    *string `json:"label_name"`
	Comment      *string `json:"comment"`
	IncomeRub    *int64  `json:"income_rub"`
	ExpenseRub   *int64  `json:"expense_rub"`
	IsBatch      bool    `json:"is_batch"`
}

type categoryInput struct {
	ClientCategoryID *string `json:"client_category_id"`
	Name             string  `json:"name"`
	TargetAmountRub  *int64  `json:"target_amount_rub"`
	SortOrder        int64   `json:"sort_order"`
	IsVisibleOnHome  *bool   `json:"is_visible_on_home"`
}

type categoryPatch struct {
	Name            *string       `json:"name"`
	TargetAmountRub nullableInt64 `json:"target_amount_rub"`
	SortOrder       *int64        `json:"sort_order"`
	IsArchived      *bool         `json:"is_archived"`
	IsVisibleOnHome *bool         `json:"is_visible_on_home"`
}

type nullableInt64 struct {
	Set   bool
	Value *int64
}

func (value *nullableInt64) UnmarshalJSON(data []byte) error {
	value.Set = true
	if string(data) == "null" {
		value.Value = nil
		return nil
	}
	var parsed int64
	if err := json.Unmarshal(data, &parsed); err != nil {
		return err
	}
	value.Value = &parsed
	return nil
}

type nullableString struct {
	Set   bool
	Value *string
}

func (value *nullableString) UnmarshalJSON(data []byte) error {
	value.Set = true
	if string(data) == "null" {
		value.Value = nil
		return nil
	}
	var parsed string
	if err := json.Unmarshal(data, &parsed); err != nil {
		return err
	}
	if strings.TrimSpace(parsed) == "" {
		return errors.New("value must not be empty")
	}
	value.Value = &parsed
	return nil
}

type labelInput struct {
	Name      string `json:"name"`
	SortOrder int64  `json:"sort_order"`
}

type labelPatch struct {
	Name       *string `json:"name"`
	SortOrder  *int64  `json:"sort_order"`
	IsArchived *bool   `json:"is_archived"`
}

type transactionInput struct {
	CategoryID        string  `json:"category_id"`
	LabelID           *string `json:"label_id"`
	AmountRub         int64   `json:"amount_rub"`
	Comment           *string `json:"comment"`
	OccurredAt        string  `json:"occurred_at"`
	ClientOperationID string  `json:"client_operation_id"`
}

type transactionPatch struct {
	LabelID nullableString `json:"label_id"`
}

type batchInput struct {
	LabelID      *string            `json:"label_id"`
	Comment      *string            `json:"comment"`
	OccurredAt   string             `json:"occurred_at"`
	Transactions []transactionInput `json:"transactions"`
}

func writeJSON(writer http.ResponseWriter, status int, value any) {
	writer.Header().Set("Content-Type", "application/json; charset=utf-8")
	writer.WriteHeader(status)
	_ = json.NewEncoder(writer).Encode(value)
}

func writeError(writer http.ResponseWriter, status int, code, message string, details map[string]any) {
	if details == nil {
		details = map[string]any{}
	}
	response := errorBody{}
	response.Error.Code, response.Error.Message, response.Error.Details = code, message, details
	writeJSON(writer, status, response)
}

func decodeJSON(writer http.ResponseWriter, request *http.Request, target any) bool {
	request.Body = http.MaxBytesReader(writer, request.Body, maxRequestBytes)
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		writeError(writer, http.StatusBadRequest, "invalid_request", "invalid JSON request body", nil)
		return false
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		writeError(writer, http.StatusBadRequest, "invalid_request", "request body must contain one JSON value", nil)
		return false
	}
	return true
}

func newID() (string, error) {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}
	return hex.EncodeToString(bytes), nil
}

func now() string { return time.Now().UTC().Format(time.RFC3339Nano) }

func validName(value string) bool { return strings.TrimSpace(value) != "" }

func validTime(value string) (string, bool) {
	parsed, err := time.Parse(time.RFC3339, value)
	if err != nil {
		return "", false
	}
	return parsed.UTC().Format(time.RFC3339Nano), true
}

func boolInt(value bool) int {
	if value {
		return 1
	}
	return 0
}

func nextRevision(ctx context.Context, tx *sql.Tx) (int64, error) {
	var value string
	if err := tx.QueryRowContext(ctx, "SELECT value FROM app_state WHERE key = 'current_revision'").Scan(&value); err != nil {
		return 0, err
	}
	revision, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return 0, err
	}
	revision++
	if _, err := tx.ExecContext(ctx, "UPDATE app_state SET value = ? WHERE key = 'current_revision'", strconv.FormatInt(revision, 10)); err != nil {
		return 0, err
	}
	return revision, nil
}

func scanCategory(row interface{ Scan(...any) error }) (category, error) {
	var item category
	var clientCategoryID sql.NullString
	var target sql.NullInt64
	var archived, visible int
	err := row.Scan(&item.ID, &clientCategoryID, &item.Name, &item.Currency, &target, &item.SortOrder, &archived, &visible, &item.CreatedAt, &item.UpdatedAt, &item.Revision)
	if clientCategoryID.Valid {
		item.ClientCategoryID = &clientCategoryID.String
	}
	if target.Valid {
		item.TargetAmountRub = &target.Int64
	}
	item.IsArchived, item.IsVisibleOnHome = archived == 1, visible == 1
	return item, err
}

func scanLabel(row interface{ Scan(...any) error }) (label, error) {
	var item label
	var archived int
	err := row.Scan(&item.ID, &item.Name, &item.SortOrder, &archived, &item.CreatedAt, &item.UpdatedAt, &item.Revision)
	item.IsArchived = archived == 1
	return item, err
}

func scanTransaction(row interface{ Scan(...any) error }) (transaction, error) {
	var item transaction
	var labelID, batchID, comment sql.NullString
	var cancelled int
	err := row.Scan(&item.ID, &item.CategoryID, &labelID, &batchID, &item.AmountRub, &comment, &item.OccurredAt, &item.CreatedAt, &item.UpdatedAt, &item.ClientOperationID, &cancelled, &item.Revision)
	if labelID.Valid {
		item.LabelID = &labelID.String
	}
	if batchID.Valid {
		item.BatchID = &batchID.String
	}
	if comment.Valid {
		item.Comment = &comment.String
	}
	item.IsCancelled = cancelled == 1
	return item, err
}

const categoryColumns = "id, client_category_id, name, currency, target_amount_rub, sort_order, is_archived, is_visible_on_home, created_at, updated_at, revision"
const labelColumns = "id, name, sort_order, is_archived, created_at, updated_at, revision"
const transactionColumns = "id, category_id, label_id, batch_id, amount_rub, comment, occurred_at, created_at, updated_at, client_operation_id, is_cancelled, revision"

func categoriesList(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		rows, err := db.QueryContext(r.Context(), "SELECT "+categoryColumns+" FROM categories ORDER BY is_archived, sort_order, id")
		if err != nil {
			internalError(w)
			return
		}
		defer rows.Close()
		items := []category{}
		for rows.Next() {
			item, err := scanCategory(rows)
			if err != nil {
				internalError(w)
				return
			}
			items = append(items, item)
		}
		if err := rows.Err(); err != nil {
			internalError(w)
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"categories": items})
	}
}

func categoryCreate(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var input categoryInput
		if !decodeJSON(w, r, &input) {
			return
		}
		if !validName(input.Name) || (input.TargetAmountRub != nil && *input.TargetAmountRub < 0) || (input.ClientCategoryID != nil && strings.TrimSpace(*input.ClientCategoryID) == "") {
			writeError(w, 400, "validation_error", "category fields are invalid", nil)
			return
		}
		id, err := newID()
		if err != nil {
			internalError(w)
			return
		}
		timestamp := now()
		visible := true
		if input.IsVisibleOnHome != nil {
			visible = *input.IsVisibleOnHome
		}
		tx, err := db.BeginTx(r.Context(), nil)
		if err != nil {
			internalError(w)
			return
		}
		defer tx.Rollback()
		if input.ClientCategoryID != nil {
			existing, lookupErr := scanCategory(tx.QueryRowContext(r.Context(), "SELECT "+categoryColumns+" FROM categories WHERE client_category_id = ?", strings.TrimSpace(*input.ClientCategoryID)))
			if lookupErr == nil {
				if err = tx.Commit(); err != nil {
					internalError(w)
					return
				}
				writeJSON(w, 200, existing)
				return
			}
			if !errors.Is(lookupErr, sql.ErrNoRows) {
				internalError(w)
				return
			}
		}
		revision, err := nextRevision(r.Context(), tx)
		if err != nil {
			internalError(w)
			return
		}
		var clientCategoryID *string
		if input.ClientCategoryID != nil {
			normalized := strings.TrimSpace(*input.ClientCategoryID)
			clientCategoryID = &normalized
		}
		_, err = tx.ExecContext(r.Context(), "INSERT INTO categories (id, client_category_id, name, target_amount_rub, sort_order, is_visible_on_home, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, clientCategoryID, strings.TrimSpace(input.Name), input.TargetAmountRub, input.SortOrder, boolInt(visible), timestamp, timestamp, revision)
		if err != nil {
			internalError(w)
			return
		}
		if err = tx.Commit(); err != nil {
			internalError(w)
			return
		}
		writeJSON(w, 201, category{ID: id, ClientCategoryID: clientCategoryID, Name: strings.TrimSpace(input.Name), Currency: "RUB", TargetAmountRub: input.TargetAmountRub, SortOrder: input.SortOrder, IsVisibleOnHome: visible, CreatedAt: timestamp, UpdatedAt: timestamp, Revision: revision})
	}
}

func categoryUpdate(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var input categoryPatch
		if !decodeJSON(w, r, &input) {
			return
		}
		if input.Name == nil && !input.TargetAmountRub.Set && input.SortOrder == nil && input.IsArchived == nil && input.IsVisibleOnHome == nil {
			writeError(w, 400, "validation_error", "at least one field is required", nil)
			return
		}
		if input.Name != nil && !validName(*input.Name) || input.TargetAmountRub.Set && input.TargetAmountRub.Value != nil && *input.TargetAmountRub.Value < 0 {
			writeError(w, 400, "validation_error", "category fields are invalid", nil)
			return
		}
		tx, err := db.BeginTx(r.Context(), nil)
		if err != nil {
			internalError(w)
			return
		}
		defer tx.Rollback()
		item, err := scanCategory(tx.QueryRowContext(r.Context(), "SELECT "+categoryColumns+" FROM categories WHERE id = ?", chi.URLParam(r, "id")))
		if errors.Is(err, sql.ErrNoRows) {
			writeError(w, 404, "not_found", "category was not found", nil)
			return
		}
		if err != nil {
			internalError(w)
			return
		}
		if input.Name != nil {
			item.Name = strings.TrimSpace(*input.Name)
		}
		if input.TargetAmountRub.Set {
			item.TargetAmountRub = input.TargetAmountRub.Value
		}
		if input.SortOrder != nil {
			item.SortOrder = *input.SortOrder
		}
		if input.IsArchived != nil {
			item.IsArchived = *input.IsArchived
		}
		if input.IsVisibleOnHome != nil {
			item.IsVisibleOnHome = *input.IsVisibleOnHome
		}
		revision, err := nextRevision(r.Context(), tx)
		if err != nil {
			internalError(w)
			return
		}
		item.UpdatedAt, item.Revision = now(), revision
		_, err = tx.ExecContext(r.Context(), "UPDATE categories SET name=?, target_amount_rub=?, sort_order=?, is_archived=?, is_visible_on_home=?, updated_at=?, revision=? WHERE id=?", item.Name, item.TargetAmountRub, item.SortOrder, boolInt(item.IsArchived), boolInt(item.IsVisibleOnHome), item.UpdatedAt, item.Revision, item.ID)
		if err != nil {
			internalError(w)
			return
		}
		if err = tx.Commit(); err != nil {
			internalError(w)
			return
		}
		writeJSON(w, 200, item)
	}
}

func labelsList(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		rows, err := db.QueryContext(r.Context(), "SELECT "+labelColumns+" FROM transaction_labels ORDER BY is_archived, sort_order, id")
		if err != nil {
			internalError(w)
			return
		}
		defer rows.Close()
		items := []label{}
		for rows.Next() {
			item, err := scanLabel(rows)
			if err != nil {
				internalError(w)
				return
			}
			items = append(items, item)
		}
		if err := rows.Err(); err != nil {
			internalError(w)
			return
		}
		writeJSON(w, 200, map[string]any{"labels": items})
	}
}

func labelCreate(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var input labelInput
		if !decodeJSON(w, r, &input) {
			return
		}
		if !validName(input.Name) {
			writeError(w, 400, "validation_error", "label name is required", nil)
			return
		}
		id, err := newID()
		if err != nil {
			internalError(w)
			return
		}
		timestamp := now()
		tx, err := db.BeginTx(r.Context(), nil)
		if err != nil {
			internalError(w)
			return
		}
		defer tx.Rollback()
		revision, err := nextRevision(r.Context(), tx)
		if err != nil {
			internalError(w)
			return
		}
		_, err = tx.ExecContext(r.Context(), "INSERT INTO transaction_labels (id,name,sort_order,created_at,updated_at,revision) VALUES (?,?,?,?,?,?)", id, strings.TrimSpace(input.Name), input.SortOrder, timestamp, timestamp, revision)
		if err != nil {
			internalError(w)
			return
		}
		if err = tx.Commit(); err != nil {
			internalError(w)
			return
		}
		writeJSON(w, 201, label{ID: id, Name: strings.TrimSpace(input.Name), SortOrder: input.SortOrder, CreatedAt: timestamp, UpdatedAt: timestamp, Revision: revision})
	}
}

func labelUpdate(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var input labelPatch
		if !decodeJSON(w, r, &input) {
			return
		}
		if input.Name == nil && input.SortOrder == nil && input.IsArchived == nil {
			writeError(w, 400, "validation_error", "at least one field is required", nil)
			return
		}
		if input.Name != nil && !validName(*input.Name) {
			writeError(w, 400, "validation_error", "label name is required", nil)
			return
		}
		tx, err := db.BeginTx(r.Context(), nil)
		if err != nil {
			internalError(w)
			return
		}
		defer tx.Rollback()
		item, err := scanLabel(tx.QueryRowContext(r.Context(), "SELECT "+labelColumns+" FROM transaction_labels WHERE id=?", chi.URLParam(r, "id")))
		if errors.Is(err, sql.ErrNoRows) {
			writeError(w, 404, "not_found", "label was not found", nil)
			return
		}
		if err != nil {
			internalError(w)
			return
		}
		if input.Name != nil {
			item.Name = strings.TrimSpace(*input.Name)
		}
		if input.SortOrder != nil {
			item.SortOrder = *input.SortOrder
		}
		if input.IsArchived != nil {
			item.IsArchived = *input.IsArchived
		}
		revision, err := nextRevision(r.Context(), tx)
		if err != nil {
			internalError(w)
			return
		}
		item.UpdatedAt, item.Revision = now(), revision
		_, err = tx.ExecContext(r.Context(), "UPDATE transaction_labels SET name=?,sort_order=?,is_archived=?,updated_at=?,revision=? WHERE id=?", item.Name, item.SortOrder, boolInt(item.IsArchived), item.UpdatedAt, item.Revision, item.ID)
		if err != nil {
			internalError(w)
			return
		}
		if err = tx.Commit(); err != nil {
			internalError(w)
			return
		}
		writeJSON(w, 200, item)
	}
}

func internalError(writer http.ResponseWriter) {
	writeError(writer, http.StatusInternalServerError, "internal_error", "internal server error", nil)
}

func validateTransaction(input transactionInput) (string, bool) {
	if strings.TrimSpace(input.CategoryID) == "" || strings.TrimSpace(input.ClientOperationID) == "" || input.AmountRub == 0 {
		return "", false
	}
	return validTime(input.OccurredAt)
}

func activeReferences(ctx context.Context, tx *sql.Tx, categoryID string, labelID *string) error {
	var archived int
	err := tx.QueryRowContext(ctx, "SELECT is_archived FROM categories WHERE id=?", categoryID).Scan(&archived)
	if errors.Is(err, sql.ErrNoRows) {
		return fmt.Errorf("category not found")
	}
	if err != nil {
		return err
	}
	if archived == 1 {
		return fmt.Errorf("category is archived")
	}
	if labelID != nil {
		err = tx.QueryRowContext(ctx, "SELECT is_archived FROM transaction_labels WHERE id=?", *labelID).Scan(&archived)
		if errors.Is(err, sql.ErrNoRows) {
			return fmt.Errorf("label not found")
		}
		if err != nil {
			return err
		}
		if archived == 1 {
			return fmt.Errorf("label is archived")
		}
	}
	return nil
}

func existingTransaction(ctx context.Context, queryer interface {
	QueryRowContext(context.Context, string, ...any) *sql.Row
}, clientOperationID string) (transaction, bool, error) {
	item, err := scanTransaction(queryer.QueryRowContext(ctx, "SELECT "+transactionColumns+" FROM transactions WHERE client_operation_id=?", clientOperationID))
	if errors.Is(err, sql.ErrNoRows) {
		return transaction{}, false, nil
	}
	return item, true, err
}

func insertTransaction(ctx context.Context, tx *sql.Tx, input transactionInput, batchID *string) (transaction, error) {
	occurredAt, ok := validateTransaction(input)
	if !ok {
		return transaction{}, fmt.Errorf("invalid transaction")
	}
	if err := activeReferences(ctx, tx, input.CategoryID, input.LabelID); err != nil {
		return transaction{}, err
	}
	id, err := newID()
	if err != nil {
		return transaction{}, err
	}
	timestamp := now()
	revision, err := nextRevision(ctx, tx)
	if err != nil {
		return transaction{}, err
	}
	_, err = tx.ExecContext(ctx, "INSERT INTO transactions (id,category_id,label_id,batch_id,amount_rub,comment,occurred_at,created_at,updated_at,client_operation_id,revision) VALUES (?,?,?,?,?,?,?,?,?,?,?)", id, input.CategoryID, input.LabelID, batchID, input.AmountRub, input.Comment, occurredAt, timestamp, timestamp, input.ClientOperationID, revision)
	if err != nil {
		return transaction{}, err
	}
	return transaction{ID: id, CategoryID: input.CategoryID, LabelID: input.LabelID, BatchID: batchID, AmountRub: input.AmountRub, Comment: input.Comment, OccurredAt: occurredAt, CreatedAt: timestamp, UpdatedAt: timestamp, ClientOperationID: input.ClientOperationID, Revision: revision}, nil
}

func transactionCreate(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var input transactionInput
		if !decodeJSON(w, r, &input) {
			return
		}
		if _, ok := validateTransaction(input); !ok {
			writeError(w, 400, "validation_error", "category, non-zero amount, date and client operation ID are required", nil)
			return
		}
		tx, err := db.BeginTx(r.Context(), nil)
		if err != nil {
			internalError(w)
			return
		}
		defer tx.Rollback()
		existing, found, err := existingTransaction(r.Context(), tx, input.ClientOperationID)
		if err != nil {
			internalError(w)
			return
		}
		if found {
			if err = tx.Commit(); err != nil {
				internalError(w)
				return
			}
			writeJSON(w, 200, map[string]any{"transaction": existing, "idempotent": true})
			return
		}
		item, err := insertTransaction(r.Context(), tx, input, nil)
		if err != nil {
			writeError(w, 400, "validation_error", "transaction or its referenced category or label is invalid", nil)
			return
		}
		if err = tx.Commit(); err != nil {
			internalError(w)
			return
		}
		writeJSON(w, 201, map[string]any{"transaction": item, "idempotent": false})
	}
}

func transactionBatchCreate(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var input batchInput
		if !decodeJSON(w, r, &input) {
			return
		}
		occurredAt, ok := validTime(input.OccurredAt)
		if !ok || len(input.Transactions) == 0 {
			writeError(w, 400, "validation_error", "date and at least one transaction are required", nil)
			return
		}
		seen := map[string]bool{}
		for index := range input.Transactions {
			input.Transactions[index].OccurredAt = occurredAt
			input.Transactions[index].LabelID = input.LabelID
			input.Transactions[index].Comment = input.Comment
			if _, ok := validateTransaction(input.Transactions[index]); !ok || seen[input.Transactions[index].ClientOperationID] {
				writeError(w, 400, "validation_error", "each batch transaction must be valid and have a unique client operation ID", nil)
				return
			}
			seen[input.Transactions[index].ClientOperationID] = true
		}
		tx, err := db.BeginTx(r.Context(), nil)
		if err != nil {
			internalError(w)
			return
		}
		defer tx.Rollback()
		existing := []transaction{}
		for _, item := range input.Transactions {
			foundItem, found, queryErr := existingTransaction(r.Context(), tx, item.ClientOperationID)
			if queryErr != nil {
				internalError(w)
				return
			}
			if found {
				existing = append(existing, foundItem)
			}
		}
		if len(existing) > 0 {
			if len(existing) != len(input.Transactions) {
				writeError(w, 409, "idempotency_conflict", "batch contains a mix of new and existing client operation IDs", nil)
				return
			}
			batchID := existing[0].BatchID
			if batchID == nil {
				writeError(w, 409, "idempotency_conflict", "existing operation is not a batch", nil)
				return
			}
			for _, item := range existing {
				if item.BatchID == nil || *item.BatchID != *batchID {
					writeError(w, 409, "idempotency_conflict", "existing operations belong to different batches", nil)
					return
				}
			}
			if err = tx.Commit(); err != nil {
				internalError(w)
				return
			}
			writeJSON(w, 200, map[string]any{"batch_id": *batchID, "transactions": existing, "idempotent": true})
			return
		}
		batchID, err := newID()
		if err != nil {
			internalError(w)
			return
		}
		created := make([]transaction, 0, len(input.Transactions))
		for _, item := range input.Transactions {
			createdItem, createErr := insertTransaction(r.Context(), tx, item, &batchID)
			if createErr != nil {
				writeError(w, 400, "validation_error", "transaction or its referenced category or label is invalid", nil)
				return
			}
			created = append(created, createdItem)
		}
		if err = tx.Commit(); err != nil {
			internalError(w)
			return
		}
		writeJSON(w, 201, map[string]any{"batch_id": batchID, "transactions": created, "idempotent": false})
	}
}

func transactionCancel(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tx, err := db.BeginTx(r.Context(), nil)
		if err != nil {
			internalError(w)
			return
		}
		defer tx.Rollback()
		item, err := scanTransaction(tx.QueryRowContext(r.Context(), "SELECT "+transactionColumns+" FROM transactions WHERE id=?", chi.URLParam(r, "id")))
		if errors.Is(err, sql.ErrNoRows) {
			writeError(w, 404, "not_found", "transaction was not found", nil)
			return
		}
		if err != nil {
			internalError(w)
			return
		}
		if !item.IsCancelled {
			revision, err := nextRevision(r.Context(), tx)
			if err != nil {
				internalError(w)
				return
			}
			item.IsCancelled, item.Revision, item.UpdatedAt = true, revision, now()
			if _, err = tx.ExecContext(r.Context(), "UPDATE transactions SET is_cancelled=1,updated_at=?,revision=? WHERE id=?", item.UpdatedAt, item.Revision, item.ID); err != nil {
				internalError(w)
				return
			}
		}
		if err = tx.Commit(); err != nil {
			internalError(w)
			return
		}
		writeJSON(w, 200, item)
	}
}

func transactionUpdate(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var input transactionPatch
		if !decodeJSON(w, r, &input) {
			return
		}
		if !input.LabelID.Set {
			writeError(w, http.StatusBadRequest, "validation_error", "label_id is required", nil)
			return
		}
		tx, err := db.BeginTx(r.Context(), nil)
		if err != nil {
			internalError(w)
			return
		}
		defer tx.Rollback()
		item, err := scanTransaction(tx.QueryRowContext(r.Context(), "SELECT "+transactionColumns+" FROM transactions WHERE id=?", chi.URLParam(r, "id")))
		if errors.Is(err, sql.ErrNoRows) {
			writeError(w, http.StatusNotFound, "not_found", "transaction was not found", nil)
			return
		}
		if err != nil {
			internalError(w)
			return
		}
		if item.IsCancelled {
			writeError(w, http.StatusConflict, "operation_not_editable", "cancelled transaction cannot be edited", nil)
			return
		}
		if input.LabelID.Value != nil {
			var archived int
			err = tx.QueryRowContext(r.Context(), "SELECT is_archived FROM transaction_labels WHERE id=?", *input.LabelID.Value).Scan(&archived)
			if errors.Is(err, sql.ErrNoRows) || archived == 1 {
				writeError(w, http.StatusBadRequest, "validation_error", "label is invalid or archived", nil)
				return
			}
			if err != nil {
				internalError(w)
				return
			}
		}
		if sameOptionalString(item.LabelID, input.LabelID.Value) {
			if err = tx.Commit(); err != nil {
				internalError(w)
				return
			}
			writeJSON(w, http.StatusOK, item)
			return
		}
		revision, err := nextRevision(r.Context(), tx)
		if err != nil {
			internalError(w)
			return
		}
		item.LabelID, item.Revision, item.UpdatedAt = input.LabelID.Value, revision, now()
		if _, err = tx.ExecContext(r.Context(), "UPDATE transactions SET label_id=?,updated_at=?,revision=? WHERE id=?", item.LabelID, item.UpdatedAt, item.Revision, item.ID); err != nil {
			internalError(w)
			return
		}
		if err = tx.Commit(); err != nil {
			internalError(w)
			return
		}
		writeJSON(w, http.StatusOK, item)
	}
}

func sameOptionalString(left, right *string) bool {
	if left == nil || right == nil {
		return left == nil && right == nil
	}
	return *left == *right
}

func transactionsList(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		query := r.URL.Query()
		where := []string{"1=1"}
		args := []any{}
		for _, pair := range []struct{ key, column string }{{"category_id", "category_id"}, {"label_id", "label_id"}} {
			if value := query.Get(pair.key); value != "" {
				where = append(where, pair.column+" = ?")
				args = append(args, value)
			}
		}
		for _, pair := range []struct{ key, op string }{{"after", ">="}, {"before", "<="}} {
			if value := query.Get(pair.key); value != "" {
				parsed, ok := validTime(value)
				if !ok {
					writeError(w, 400, "validation_error", "invalid "+pair.key+" date", nil)
					return
				}
				where = append(where, "occurred_at "+pair.op+" ?")
				args = append(args, parsed)
			}
		}
		if query.Get("include_cancelled") != "true" {
			where = append(where, "is_cancelled=0")
		}
		if cursor := query.Get("cursor"); cursor != "" {
			var occurred string
			if err := db.QueryRowContext(r.Context(), "SELECT occurred_at FROM transactions WHERE id=?", cursor).Scan(&occurred); errors.Is(err, sql.ErrNoRows) {
				writeError(w, 400, "validation_error", "invalid cursor", nil)
				return
			} else if err != nil {
				internalError(w)
				return
			}
			where = append(where, "(occurred_at < ? OR (occurred_at = ? AND id < ?))")
			args = append(args, occurred, occurred, cursor)
		}
		limit := 50
		if text := query.Get("limit"); text != "" {
			parsed, err := strconv.Atoi(text)
			if err != nil || parsed < 1 || parsed > 200 {
				writeError(w, 400, "validation_error", "limit must be between 1 and 200", nil)
				return
			}
			limit = parsed
		}
		args = append(args, limit)
		rows, err := db.QueryContext(r.Context(), "SELECT "+transactionColumns+" FROM transactions WHERE "+strings.Join(where, " AND ")+" ORDER BY occurred_at DESC,id DESC LIMIT ?", args...)
		if err != nil {
			internalError(w)
			return
		}
		defer rows.Close()
		items := []transaction{}
		for rows.Next() {
			item, err := scanTransaction(rows)
			if err != nil {
				internalError(w)
				return
			}
			items = append(items, item)
		}
		if err = rows.Err(); err != nil {
			internalError(w)
			return
		}
		var nextCursor *string
		if len(items) == limit {
			nextCursor = &items[len(items)-1].ID
		}
		writeJSON(w, 200, map[string]any{"transactions": items, "next_cursor": nextCursor})
	}
}

func webHistoryList(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		limit := 50
		if text := r.URL.Query().Get("limit"); text != "" {
			parsed, err := strconv.Atoi(text)
			if err != nil || parsed < 1 || parsed > 100 {
				writeError(w, http.StatusBadRequest, "validation_error", "limit must be between 1 and 100", nil)
				return
			}
			limit = parsed
		}

		where := []string{"t.is_cancelled=0", "c.is_archived=0"}
		args := []any{}
		if cursor := r.URL.Query().Get("cursor"); cursor != "" {
			var occurredAt string
			err := db.QueryRowContext(r.Context(), `SELECT t.occurred_at
				FROM transactions t JOIN categories c ON c.id=t.category_id
				WHERE t.id=? AND t.is_cancelled=0 AND c.is_archived=0`, cursor).Scan(&occurredAt)
			if errors.Is(err, sql.ErrNoRows) {
				writeError(w, http.StatusBadRequest, "validation_error", "invalid cursor", nil)
				return
			}
			if err != nil {
				internalError(w)
				return
			}
			where = append(where, "(t.occurred_at < ? OR (t.occurred_at = ? AND t.id < ?))")
			args = append(args, occurredAt, occurredAt, cursor)
		}

		args = append(args, limit+1)
		rows, err := db.QueryContext(r.Context(), `SELECT t.id,t.occurred_at,c.name,t.label_id,l.name,t.comment,t.amount_rub,t.batch_id
			FROM transactions t
			JOIN categories c ON c.id=t.category_id
			LEFT JOIN transaction_labels l ON l.id=t.label_id
			WHERE `+strings.Join(where, " AND ")+`
			ORDER BY t.occurred_at DESC,t.id DESC LIMIT ?`, args...)
		if err != nil {
			internalError(w)
			return
		}
		defer rows.Close()

		items := make([]webHistoryTransaction, 0, limit)
		for rows.Next() {
			var item webHistoryTransaction
			var labelID, labelName, comment, batchID sql.NullString
			var amount int64
			if err := rows.Scan(&item.ID, &item.OccurredAt, &item.CategoryName, &labelID, &labelName, &comment, &amount, &batchID); err != nil {
				internalError(w)
				return
			}
			if labelName.Valid {
				item.LabelName = &labelName.String
			}
			if labelID.Valid {
				item.LabelID = &labelID.String
			}
			if comment.Valid {
				item.Comment = &comment.String
			}
			item.IsBatch = batchID.Valid
			if amount > 0 {
				item.IncomeRub = &amount
			} else {
				expense := -amount
				item.ExpenseRub = &expense
			}
			items = append(items, item)
		}
		if err := rows.Err(); err != nil {
			internalError(w)
			return
		}

		var nextCursor *string
		if len(items) > limit {
			nextCursor = &items[limit-1].ID
			items = items[:limit]
		}
		writeJSON(w, http.StatusOK, map[string]any{"transactions": items, "next_cursor": nextCursor})
	}
}

func changesGet(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		after := int64(0)
		if text := r.URL.Query().Get("after"); text != "" {
			parsed, err := strconv.ParseInt(text, 10, 64)
			if err != nil || parsed < 0 {
				writeError(w, 400, "validation_error", "after must be a non-negative revision", nil)
				return
			}
			after = parsed
		}
		var currentText string
		if err := db.QueryRowContext(r.Context(), "SELECT value FROM app_state WHERE key='current_revision'").Scan(&currentText); err != nil {
			internalError(w)
			return
		}
		current, err := strconv.ParseInt(currentText, 10, 64)
		if err != nil {
			internalError(w)
			return
		}
		categories, err := changedCategories(r.Context(), db, after)
		if err != nil {
			internalError(w)
			return
		}
		labels, err := changedLabels(r.Context(), db, after)
		if err != nil {
			internalError(w)
			return
		}
		transactions, err := changedTransactions(r.Context(), db, after)
		if err != nil {
			internalError(w)
			return
		}
		writeJSON(w, 200, map[string]any{"categories": categories, "labels": labels, "transactions": transactions, "revision": current})
	}
}

func changedCategories(ctx context.Context, db *sql.DB, after int64) ([]category, error) {
	rows, err := db.QueryContext(ctx, "SELECT "+categoryColumns+" FROM categories WHERE revision>? ORDER BY revision,id", after)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []category{}
	for rows.Next() {
		item, err := scanCategory(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}
func changedLabels(ctx context.Context, db *sql.DB, after int64) ([]label, error) {
	rows, err := db.QueryContext(ctx, "SELECT "+labelColumns+" FROM transaction_labels WHERE revision>? ORDER BY revision,id", after)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []label{}
	for rows.Next() {
		item, err := scanLabel(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}
func changedTransactions(ctx context.Context, db *sql.DB, after int64) ([]transaction, error) {
	rows, err := db.QueryContext(ctx, "SELECT "+transactionColumns+" FROM transactions WHERE revision>? ORDER BY revision,id", after)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []transaction{}
	for rows.Next() {
		item, err := scanTransaction(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func summaryGet(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		rows, err := db.QueryContext(r.Context(), `SELECT c.id,c.name,c.target_amount_rub,COALESCE(SUM(CASE WHEN t.is_cancelled=0 THEN t.amount_rub ELSE 0 END),0),MAX(t.occurred_at) FROM categories c LEFT JOIN transactions t ON t.category_id=c.id WHERE c.is_archived=0 GROUP BY c.id ORDER BY c.sort_order,c.id`)
		if err != nil {
			internalError(w)
			return
		}
		defer rows.Close()
		type item struct {
			ID             string  `json:"id"`
			Name           string  `json:"name"`
			Target         *int64  `json:"target_amount_rub"`
			Balance        int64   `json:"balance_rub"`
			LastOccurredAt *string `json:"last_occurred_at"`
		}
		items := []item{}
		var total int64
		for rows.Next() {
			var value item
			var target sql.NullInt64
			var last sql.NullString
			if err := rows.Scan(&value.ID, &value.Name, &target, &value.Balance, &last); err != nil {
				internalError(w)
				return
			}
			if target.Valid {
				value.Target = &target.Int64
			}
			if last.Valid {
				value.LastOccurredAt = &last.String
			}
			total += value.Balance
			items = append(items, value)
		}
		if err = rows.Err(); err != nil {
			internalError(w)
			return
		}
		var current string
		if err = db.QueryRowContext(r.Context(), "SELECT value FROM app_state WHERE key='current_revision'").Scan(&current); err != nil {
			internalError(w)
			return
		}
		revision, err := strconv.ParseInt(current, 10, 64)
		if err != nil {
			internalError(w)
			return
		}
		writeJSON(w, 200, map[string]any{"total_balance_rub": total, "categories": items, "revision": revision})
	}
}
