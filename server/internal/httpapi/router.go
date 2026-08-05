package httpapi

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"
	"reserve-sin/server/internal/logging"
)

func NewRouter(logger *slog.Logger) http.Handler {
	router := chi.NewRouter()
	router.Get("/health", health)
	return logging.WithRequestLogging(logger, router)
}

func health(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(writer).Encode(map[string]string{
		"status":  "ok",
		"database": "not_configured",
		"version": "not_configured",
	})
}
