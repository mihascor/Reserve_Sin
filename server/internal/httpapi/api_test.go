package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"

	"reserve-sin/server/internal/database"
	"reserve-sin/server/internal/logging"
)

func TestAPIRequiresBearerToken(t *testing.T) {
	router := testRouter(t)
	response := httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/api/v1/categories", nil))
	if response.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusUnauthorized)
	}
	if response.Header().Get("WWW-Authenticate") != "Bearer" {
		t.Fatal("missing Bearer authentication challenge")
	}
}

func TestTransactionCreationIsIdempotentAndVisibleInChanges(t *testing.T) {
	router := testRouter(t)
	categoryResponse := sendJSON(t, router, http.MethodPost, "/api/v1/categories", `{"name":"Подушка","sort_order":0}`)
	if categoryResponse.Code != http.StatusCreated {
		t.Fatalf("create category status = %d, body = %s", categoryResponse.Code, categoryResponse.Body.String())
	}
	var createdCategory category
	if err := json.NewDecoder(categoryResponse.Body).Decode(&createdCategory); err != nil {
		t.Fatalf("decode category: %v", err)
	}

	body := `{"category_id":"` + createdCategory.ID + `","amount_rub":100,"occurred_at":"2026-08-05T10:00:00Z","client_operation_id":"phone-operation-1"}`
	first := sendJSON(t, router, http.MethodPost, "/api/v1/transactions", body)
	if first.Code != http.StatusCreated {
		t.Fatalf("first transaction status = %d, body = %s", first.Code, first.Body.String())
	}
	second := sendJSON(t, router, http.MethodPost, "/api/v1/transactions", body)
	if second.Code != http.StatusOK {
		t.Fatalf("idempotent transaction status = %d, body = %s", second.Code, second.Body.String())
	}
	var repeated struct { Idempotent bool `json:"idempotent"` }
	if err := json.NewDecoder(second.Body).Decode(&repeated); err != nil {
		t.Fatalf("decode repeated response: %v", err)
	}
	if !repeated.Idempotent {
		t.Fatal("repeated request is not marked idempotent")
	}

	changes := sendJSON(t, router, http.MethodGet, "/api/v1/changes?after=0", "")
	if changes.Code != http.StatusOK {
		t.Fatalf("changes status = %d, body = %s", changes.Code, changes.Body.String())
	}
	var result struct { Transactions []transaction `json:"transactions"`; Revision int64 `json:"revision"` }
	if err := json.NewDecoder(changes.Body).Decode(&result); err != nil {
		t.Fatalf("decode changes: %v", err)
	}
	if len(result.Transactions) != 1 || result.Revision != 2 {
		t.Fatalf("changes = %+v, want one transaction and revision 2", result)
	}
}

func testRouter(t *testing.T) http.Handler {
	t.Helper()
	ctx := context.Background()
	db, err := database.Open(ctx, ":memory:")
	if err != nil { t.Fatalf("open database: %v", err) }
	t.Cleanup(func() { _ = db.Close() })
	if err := database.ApplyMigrations(ctx, db); err != nil { t.Fatalf("apply migrations: %v", err) }
	return NewRouter(logging.New(logging.Config{Environment: logging.EnvironmentDevelopment, Level: slog.LevelError}, &bytes.Buffer{}), db, "test-token")
}

func sendJSON(t *testing.T, router http.Handler, method, path, body string) *httptest.ResponseRecorder {
	t.Helper()
	request := httptest.NewRequest(method, path, bytes.NewBufferString(body))
	request.Header.Set("Authorization", "Bearer test-token")
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	return response
}
