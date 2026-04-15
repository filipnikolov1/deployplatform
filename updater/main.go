package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os/exec"
)

var allowedServices = map[string]bool{
	"launchpad":          true,
	"launchpad-frontend": true,
}

type updateRequest struct {
	Service string `json:"service"`
	Image   string `json:"image"`
}

type updateResponse struct {
	Status string `json:"status"`
	Error  string `json:"error,omitempty"`
}

func main() {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"ok"}`))
	})
	mux.HandleFunc("/update", handleUpdate)

	log.Println("launchpad-updater listening on :8080")
	if err := http.ListenAndServe(":8080", mux); err != nil {
		log.Fatal(err)
	}
}

func handleUpdate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "POST only", http.StatusMethodNotAllowed)
		return
	}
	var req updateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, updateResponse{Status: "error", Error: "invalid json"})
		return
	}
	if !allowedServices[req.Service] {
		writeJSON(w, http.StatusForbidden, updateResponse{Status: "error", Error: "service not in allowlist: " + req.Service})
		return
	}
	if req.Image == "" {
		writeJSON(w, http.StatusBadRequest, updateResponse{Status: "error", Error: "image is required"})
		return
	}

	log.Printf("pulling %s", req.Image)
	if out, err := exec.Command("docker", "pull", req.Image).CombinedOutput(); err != nil {
		log.Printf("pull failed: %s", string(out))
		writeJSON(w, http.StatusInternalServerError, updateResponse{Status: "error", Error: "pull failed: " + err.Error()})
		return
	}

	log.Printf("recreating service %s", req.Service)
	if out, err := exec.Command("docker", "compose", "-f", "/workspace/docker-compose.yml", "up", "-d", "--no-deps", req.Service).CombinedOutput(); err != nil {
		log.Printf("recreate failed: %s", string(out))
		writeJSON(w, http.StatusInternalServerError, updateResponse{Status: "error", Error: "recreate failed: " + err.Error()})
		return
	}

	writeJSON(w, http.StatusOK, updateResponse{Status: "ok"})
}

func writeJSON(w http.ResponseWriter, status int, body updateResponse) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
