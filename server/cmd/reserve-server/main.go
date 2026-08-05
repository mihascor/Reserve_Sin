package main

import (
	"log/slog"
	"net/http"
	"os"

	"reserve-sin/server/internal/httpapi"
	"reserve-sin/server/internal/logging"
)

func main() {
	logger, err := logging.NewFromEnvironment()
	if err != nil {
		slog.New(slog.NewTextHandler(os.Stderr, nil)).Error("invalid logging configuration", "error", err)
		os.Exit(1)
	}

	server := &http.Server{
		Addr:    "127.0.0.1:8080",
		Handler: httpapi.NewRouter(logger),
	}

	logger.Info("server starting", "address", server.Addr)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		logging.ErrorWithStack(logger, "server stopped unexpectedly", err, "address", server.Addr)
		os.Exit(1)
	}
}
