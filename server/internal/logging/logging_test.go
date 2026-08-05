package logging

import (
	"bytes"
	"encoding/json"
	"log/slog"
	"strings"
	"testing"
)

func TestConfigFromEnvironment(t *testing.T) {
	config, err := ConfigFromEnvironment(func(key string) string {
		return map[string]string{
			EnvEnvironment: EnvironmentProduction,
			EnvLogLevel:    "warning",
		}[key]
	})
	if err != nil {
		t.Fatalf("ConfigFromEnvironment() error = %v", err)
	}
	if config.Environment != EnvironmentProduction || config.Level != slog.LevelWarn {
		t.Fatalf("ConfigFromEnvironment() = %#v, want production/warning", config)
	}
}

func TestNewUsesJSONInProduction(t *testing.T) {
	var output bytes.Buffer
	New(Config{Environment: EnvironmentProduction, Level: slog.LevelInfo}, &output).Info("server started", "request_id", "request-1")

	var record map[string]any
	if err := json.Unmarshal(output.Bytes(), &record); err != nil {
		t.Fatalf("production output is not JSON: %v", err)
	}
	if record["request_id"] != "request-1" {
		t.Fatalf("request_id = %v, want request-1", record["request_id"])
	}
}

func TestNewUsesTextInDevelopment(t *testing.T) {
	var output bytes.Buffer
	New(Config{Environment: EnvironmentDevelopment, Level: slog.LevelInfo}, &output).Info("server started")
	if !strings.Contains(output.String(), "msg=\"server started\"") {
		t.Fatalf("development output is not readable text: %s", output.String())
	}
}
