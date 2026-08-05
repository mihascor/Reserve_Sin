package auth

import (
	"crypto/sha256"
	"crypto/subtle"
	"fmt"
	"net/http"
	"os"
	"strings"
)

const EnvAPIToken = "RESERVE_SIN_API_TOKEN"

func TokenFromEnvironment() (string, error) {
	token := strings.TrimSpace(os.Getenv(EnvAPIToken))
	if token == "" {
		return "", fmt.Errorf("%s must be set", EnvAPIToken)
	}
	return token, nil
}

func RequireBearer(token string, next http.Handler) http.Handler {
	expected := sha256.Sum256([]byte(token))
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		provided, ok := strings.CutPrefix(request.Header.Get("Authorization"), "Bearer ")
		actual := sha256.Sum256([]byte(provided))
		if !ok || provided == "" || subtle.ConstantTimeCompare(expected[:], actual[:]) != 1 {
			writer.Header().Set("Content-Type", "application/json; charset=utf-8")
			writer.Header().Set("WWW-Authenticate", "Bearer")
			writer.WriteHeader(http.StatusUnauthorized)
			_, _ = writer.Write([]byte(`{"error":{"code":"unauthorized","message":"authentication is required","details":{}}}`))
			return
		}
		next.ServeHTTP(writer, request)
	})
}
