package httpapi

import (
	"database/sql"
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"
	"reserve-sin/server/internal/auth"
	"reserve-sin/server/internal/logging"
)

func NewRouter(logger *slog.Logger, db *sql.DB, apiToken string) http.Handler {
	router := chi.NewRouter()
	router.Get("/health", health(db))
	router.Route("/api/v1", func(api chi.Router) {
		api.Use(func(next http.Handler) http.Handler { return auth.RequireBearer(apiToken, next) })
		api.Get("/categories", categoriesList(db))
		api.Post("/categories", categoryCreate(db))
		api.Patch("/categories/{id}", categoryUpdate(db))
		api.Get("/labels", labelsList(db))
		api.Post("/labels", labelCreate(db))
		api.Patch("/labels/{id}", labelUpdate(db))
		api.Get("/transactions", transactionsList(db))
		api.Post("/transactions", transactionCreate(db))
		api.Post("/transaction-batches", transactionBatchCreate(db))
		api.Patch("/transactions/{id}", transactionUpdateNotSupported)
		api.Post("/transactions/{id}/cancel", transactionCancel(db))
		api.Get("/summary", summaryGet(db))
		api.Get("/changes", changesGet(db))
	})
	return logging.WithRequestLogging(logger, router)
}

func health(db *sql.DB) http.HandlerFunc {
	return func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("Content-Type", "application/json; charset=utf-8")
		if err := db.PingContext(request.Context()); err != nil {
			writer.WriteHeader(http.StatusServiceUnavailable)
			_ = json.NewEncoder(writer).Encode(map[string]string{"status": "unavailable", "database": "unavailable"})
			return
		}
		_ = json.NewEncoder(writer).Encode(map[string]string{
			"status":  "ok",
			"database": "ok",
			"version": "not_configured",
		})
	}
}
