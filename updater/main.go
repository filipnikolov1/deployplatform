package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"os/exec"
	"sort"
	"strings"
)

const keepImageTags = 3

var allowedServices = map[string]bool{
	"launchpad":          true,
	"launchpad-frontend": true,
}

var imageEnvByService = map[string]string{
	"launchpad":          "LAUNCHPAD_BACKEND_IMAGE",
	"launchpad-frontend": "LAUNCHPAD_FRONTEND_IMAGE",
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
	cmd := exec.Command("docker", "compose", "--env-file", "/workspace/.env", "-f", "/workspace/docker-compose.yml", "up", "-d", "--force-recreate", "--no-deps", req.Service)
	cmd.Env = append(os.Environ(), imageEnvByService[req.Service]+"="+req.Image)
	if out, err := cmd.CombinedOutput(); err != nil {
		log.Printf("recreate failed: %s", string(out))
		writeJSON(w, http.StatusInternalServerError, updateResponse{Status: "error", Error: "recreate failed: " + err.Error()})
		return
	}

	pruneOldImages(req.Image)

	writeJSON(w, http.StatusOK, updateResponse{Status: "ok"})
}

// pruneOldImages removes old git-* tagged images for the given repo,
// keeping only the newest keepImageTags entries. Other tags (e.g. :latest) are left alone.
func pruneOldImages(image string) {
	repo := strings.SplitN(image, ":", 2)[0]
	if repo == "" {
		return
	}

	out, err := exec.Command(
		"docker", "images",
		"--format", "{{.ID}}|{{.Repository}}:{{.Tag}}|{{.CreatedAt}}",
		repo,
	).Output()
	if err != nil {
		log.Printf("prune: list failed for %s: %v", repo, err)
		return
	}

	type entry struct {
		id        string
		ref       string
		createdAt string
	}
	var candidates []entry
	for _, line := range strings.Split(strings.TrimSpace(string(out)), "\n") {
		if line == "" {
			continue
		}
		parts := strings.SplitN(line, "|", 3)
		if len(parts) != 3 {
			continue
		}
		tag := strings.TrimPrefix(parts[1], repo+":")
		if !strings.HasPrefix(tag, "git-") {
			continue
		}
		candidates = append(candidates, entry{id: parts[0], ref: parts[1], createdAt: parts[2]})
	}

	if len(candidates) <= keepImageTags {
		return
	}

	sort.Slice(candidates, func(i, j int) bool {
		return candidates[i].createdAt > candidates[j].createdAt
	})

	for _, e := range candidates[keepImageTags:] {
		log.Printf("prune: removing %s (%s)", e.ref, e.id)
		if out, err := exec.Command("docker", "rmi", e.ref).CombinedOutput(); err != nil {
			log.Printf("prune: rmi %s failed: %s", e.ref, strings.TrimSpace(string(out)))
		}
	}
}

func writeJSON(w http.ResponseWriter, status int, body updateResponse) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
