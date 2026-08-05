package logging

import (
	"fmt"
	"io"
	"log/slog"
	"os"
	"strings"
)

const (
	EnvironmentDevelopment = "development"
	EnvironmentProduction  = "production"

	EnvEnvironment = "RESERVE_SIN_ENV"
	EnvLogLevel    = "RESERVE_SIN_LOG_LEVEL"
)

type Config struct {
	Environment string
	Level       slog.Level
}

func ConfigFromEnvironment(getenv func(string) string) (Config, error) {
	environment := getenv(EnvEnvironment)
	if environment == "" {
		environment = EnvironmentDevelopment
	}
	if environment != EnvironmentDevelopment && environment != EnvironmentProduction {
		return Config{}, fmt.Errorf("%s must be %q or %q", EnvEnvironment, EnvironmentDevelopment, EnvironmentProduction)
	}

	level, err := parseLevel(getenv(EnvLogLevel))
	if err != nil {
		return Config{}, err
	}

	return Config{Environment: environment, Level: level}, nil
}

func NewFromEnvironment() (*slog.Logger, error) {
	config, err := ConfigFromEnvironment(os.Getenv)
	if err != nil {
		return nil, err
	}
	return New(config, os.Stdout), nil
}

func New(config Config, output io.Writer) *slog.Logger {
	options := &slog.HandlerOptions{Level: config.Level}
	if config.Environment == EnvironmentProduction {
		return slog.New(slog.NewJSONHandler(output, options))
	}
	return slog.New(slog.NewTextHandler(output, options))
}

func parseLevel(value string) (slog.Level, error) {
	switch strings.ToLower(value) {
	case "", "info":
		return slog.LevelInfo, nil
	case "debug":
		return slog.LevelDebug, nil
	case "warning":
		return slog.LevelWarn, nil
	case "error":
		return slog.LevelError, nil
	default:
		return 0, fmt.Errorf("%s must be debug, info, warning, or error", EnvLogLevel)
	}
}
