package logging

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"log/slog"
	"net/http"
	"runtime/debug"
	"time"
)

type contextKey struct{}

func WithRequestLogging(logger *slog.Logger, next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		requestID := newRequestID()
		requestLogger := logger.With("request_id", requestID)
		request = request.WithContext(WithLogger(request.Context(), requestLogger))

		writer.Header().Set("X-Request-ID", requestID)
		startedAt := time.Now()
		statusWriter := &statusWriter{ResponseWriter: writer, status: http.StatusOK}

		defer func() {
			if recovered := recover(); recovered != nil {
				requestLogger.Error("panic while handling request",
					"method", request.Method,
					"path", request.URL.Path,
					"error", recovered,
					"stack", string(debug.Stack()),
				)
				if !statusWriter.wroteHeader {
					statusWriter.WriteHeader(http.StatusInternalServerError)
				}
			}

			requestLogger.Info("request completed",
				"method", request.Method,
				"path", request.URL.Path,
				"status", statusWriter.status,
				"duration_ms", time.Since(startedAt).Milliseconds(),
			)
		}()

		next.ServeHTTP(statusWriter, request)
	})
}

func WithLogger(ctx context.Context, logger *slog.Logger) context.Context {
	return context.WithValue(ctx, contextKey{}, logger)
}

func LoggerFromContext(ctx context.Context, fallback *slog.Logger) *slog.Logger {
	logger, ok := ctx.Value(contextKey{}).(*slog.Logger)
	if !ok || logger == nil {
		return fallback
	}
	return logger
}

func ErrorWithStack(logger *slog.Logger, message string, err error, attributes ...any) {
	attributes = append(attributes, "error", err, "stack", string(debug.Stack()))
	logger.Error(message, attributes...)
}

type statusWriter struct {
	http.ResponseWriter
	status      int
	wroteHeader bool
}

func (writer *statusWriter) WriteHeader(status int) {
	if writer.wroteHeader {
		return
	}
	writer.status = status
	writer.wroteHeader = true
	writer.ResponseWriter.WriteHeader(status)
}

func (writer *statusWriter) Write(body []byte) (int, error) {
	if !writer.wroteHeader {
		writer.WriteHeader(http.StatusOK)
	}
	return writer.ResponseWriter.Write(body)
}

func newRequestID() string {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err == nil {
		return hex.EncodeToString(bytes)
	}
	return "generated-" + time.Now().UTC().Format("20060102T150405.000000000")
}
