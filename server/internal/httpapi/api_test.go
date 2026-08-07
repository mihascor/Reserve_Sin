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
	var repeated struct {
		Idempotent bool `json:"idempotent"`
	}
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
	var result struct {
		Transactions []transaction `json:"transactions"`
		Revision     int64         `json:"revision"`
	}
	if err := json.NewDecoder(changes.Body).Decode(&result); err != nil {
		t.Fatalf("decode changes: %v", err)
	}
	if len(result.Transactions) != 1 || result.Revision != 2 {
		t.Fatalf("changes = %+v, want one transaction and revision 2", result)
	}
}

func TestCategoryCreationIsIdempotentByClientCategoryID(t *testing.T) {
	router := testRouter(t)
	body := `{"name":"Подушка","sort_order":0,"client_category_id":"android-category-1"}`
	first := sendJSON(t, router, http.MethodPost, "/api/v1/categories", body)
	if first.Code != http.StatusCreated {
		t.Fatalf("first category status = %d, body = %s", first.Code, first.Body.String())
	}
	second := sendJSON(t, router, http.MethodPost, "/api/v1/categories", body)
	if second.Code != http.StatusOK {
		t.Fatalf("repeated category status = %d, body = %s", second.Code, second.Body.String())
	}
	var returnedCategory category
	if err := json.NewDecoder(second.Body).Decode(&returnedCategory); err != nil {
		t.Fatalf("decode category: %v", err)
	}
	if returnedCategory.ClientCategoryID == nil || *returnedCategory.ClientCategoryID != "android-category-1" {
		t.Fatalf("unexpected client category ID: %+v", returnedCategory.ClientCategoryID)
	}
	response := sendJSON(t, router, http.MethodGet, "/api/v1/categories", "")
	var list struct {
		Categories []category `json:"categories"`
	}
	if err := json.NewDecoder(response.Body).Decode(&list); err != nil {
		t.Fatalf("decode category list: %v", err)
	}
	if response.Code != http.StatusOK {
		t.Fatalf("category list status = %d", response.Code)
	}
	if len(list.Categories) != 1 {
		t.Fatalf("category count = %d, want 1", len(list.Categories))
	}
}

func TestCategoryPatchClearsTargetAmount(t *testing.T) {
	router := testRouter(t)
	created := sendJSON(t, router, http.MethodPost, "/api/v1/categories", `{"name":"Подушка","target_amount_rub":100000,"sort_order":0}`)
	if created.Code != http.StatusCreated {
		t.Fatalf("create category status = %d, body = %s", created.Code, created.Body.String())
	}
	var category category
	if err := json.NewDecoder(created.Body).Decode(&category); err != nil {
		t.Fatalf("decode category: %v", err)
	}

	updated := sendJSON(t, router, http.MethodPatch, "/api/v1/categories/"+category.ID, `{"target_amount_rub":null}`)
	if updated.Code != http.StatusOK {
		t.Fatalf("update category status = %d, body = %s", updated.Code, updated.Body.String())
	}
	if err := json.NewDecoder(updated.Body).Decode(&category); err != nil {
		t.Fatalf("decode updated category: %v", err)
	}
	if category.TargetAmountRub != nil {
		t.Fatalf("target amount = %v, want nil", *category.TargetAmountRub)
	}
}

func testRouter(t *testing.T) http.Handler {
	t.Helper()
	ctx := context.Background()
	db, err := database.Open(ctx, ":memory:")
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	t.Cleanup(func() { _ = db.Close() })
	if err := database.ApplyMigrations(ctx, db); err != nil {
		t.Fatalf("apply migrations: %v", err)
	}
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
