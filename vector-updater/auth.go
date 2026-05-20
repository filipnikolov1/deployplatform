package main

import (
	"crypto/subtle"
	"encoding/json"
	"log"
	"net/http"
	"os"
)

var updaterAuthToken string

func init() {
	updaterAuthToken = os.Getenv("VECTOR_UPDATER_AUTH_TOKEN")
	if updaterAuthToken == "" {
		log.Println("WARNING: VECTOR_UPDATER_AUTH_TOKEN is not set — /update and /status/ endpoints are unauthenticated")
	}
}

func requireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if updaterAuthToken == "" {
			next.ServeHTTP(w, r)
			return
		}
		provided := r.Header.Get("X-Updater-Auth")
		if subtle.ConstantTimeCompare([]byte(provided), []byte(updaterAuthToken)) != 1 {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusUnauthorized)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "unauthorized"})
			return
		}
		next.ServeHTTP(w, r)
	})
}
