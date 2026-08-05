package httpapi

import (
	"bytes"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"reserve-sin/server/internal/logging"
)

func TestHealth(t *testing.T) {
	var logs bytes.Buffer
	logger := logging.New(logging.Config{Environment: logging.EnvironmentDevelopment, Level: slog.LevelInfo}, &logs)
	request := httptest.NewRequest(http.MethodGet, "/health", nil)
	request.Header.Set("X-Request-ID", "secret-request-id")
	request.Header.Set("Authorization", "Bearer secret-token")
	response := httptest.NewRecorder()

	NewRouter(logger).ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	if contentType := response.Header().Get("Content-Type"); contentType != "application/json; charset=utf-8" {
		t.Fatalf("Content-Type = %q, want JSON", contentType)
	}
	requestID := response.Header().Get("X-Request-ID")
	if requestID == "" || requestID == "secret-request-id" {
		t.Fatalf("X-Request-ID = %q, want server-generated request ID", requestID)
	}
	if actualLogs := logs.String(); !strings.Contains(actualLogs, "request_id="+requestID) {
		t.Fatalf("logs do not contain request ID: %s", actualLogs)
	} else if strings.Contains(actualLogs, "secret-token") || strings.Contains(actualLogs, "secret-request-id") {
		t.Fatalf("logs contain a confidential request header: %s", actualLogs)
	}
}
