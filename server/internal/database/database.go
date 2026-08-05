package database

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"strings"

	_ "github.com/mattn/go-sqlite3"
)

const (
	EnvDatabasePath     = "RESERVE_SIN_DATABASE_PATH"
	defaultDatabasePath = "reserve.db"
)

// PathFromEnvironment returns a local database path when it is not configured.
// Production configuration must set RESERVE_SIN_DATABASE_PATH explicitly.
func PathFromEnvironment(getenv func(string) string) (string, error) {
	path := strings.TrimSpace(getenv(EnvDatabasePath))
	if path == "" {
		return defaultDatabasePath, nil
	}
	if path == ":memory:" || strings.HasPrefix(path, "file:") {
		return path, nil
	}
	if strings.ContainsRune(path, '\x00') {
		return "", fmt.Errorf("%s must not contain a null byte", EnvDatabasePath)
	}
	return path, nil
}

func PathFromCurrentEnvironment() (string, error) {
	return PathFromEnvironment(os.Getenv)
}

// Open configures a SQLite connection for the single-user server.
func Open(ctx context.Context, path string) (*sql.DB, error) {
	db, err := sql.Open("sqlite3", path)
	if err != nil {
		return nil, fmt.Errorf("open SQLite database: %w", err)
	}

	// SQLite pragmas apply per connection. A single connection keeps them stable
	// and is sufficient for this personal single-user service.
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	for _, statement := range []string{
		"PRAGMA foreign_keys = ON",
		"PRAGMA busy_timeout = 5000",
		"PRAGMA journal_mode = WAL",
	} {
		if _, err := db.ExecContext(ctx, statement); err != nil {
			_ = db.Close()
			return nil, fmt.Errorf("configure SQLite: %w", err)
		}
	}
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("ping SQLite database: %w", err)
	}
	return db, nil
}
