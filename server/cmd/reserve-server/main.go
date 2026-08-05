package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"

	"reserve-sin/server/internal/auth"
	"reserve-sin/server/internal/database"
	"reserve-sin/server/internal/httpapi"
	"reserve-sin/server/internal/logging"
)

func main() {
	logger, err := logging.NewFromEnvironment()
	if err != nil {
		slog.New(slog.NewTextHandler(os.Stderr, nil)).Error("invalid logging configuration", "error", err)
		os.Exit(1)
	}
	apiToken, err := auth.TokenFromEnvironment()
	if err != nil {
		logger.Error("invalid authentication configuration", "error", err)
		os.Exit(1)
	}
	databasePath, err := database.PathFromCurrentEnvironment()
	if err != nil {
		logger.Error("invalid database configuration", "error", err)
		os.Exit(1)
	}
	db, err := database.Open(context.Background(), databasePath)
	if err != nil {
		logging.ErrorWithStack(logger, "open database", err)
		os.Exit(1)
	}
	defer db.Close()
	if err := database.ApplyMigrations(context.Background(), db); err != nil {
		logging.ErrorWithStack(logger, "apply database migrations", err)
		os.Exit(1)
	}

	server := &http.Server{
		Addr:    "127.0.0.1:8080",
		Handler: httpapi.NewRouter(logger, db, apiToken),
	}

	logger.Info("server starting", "address", server.Addr)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		logging.ErrorWithStack(logger, "server stopped unexpectedly", err, "address", server.Addr)
		os.Exit(1)
	}
}
