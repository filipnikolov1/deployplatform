package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"os/exec"
	"sort"
	"strings"
	"sync"
	"time"
)

const keepImageTags = 3

const (
	pullTimeout     = 10 * time.Minute
	recreateTimeout = 5 * time.Minute
)

var allowedServices = map[string]bool{
	"vector-api":      true,
	"vector-web":      true,
	"vector-analyzer": true,
}

// serviceAliases maps legacy service names (pre-rename) to current Compose service names.
var serviceAliases = map[string]string{
	"vector": "vector-api",
}

var imageEnvByService = map[string]string{
	"vector-api":      "VECTOR_BACKEND_IMAGE",
	"vector-web":      "VECTOR_FRONTEND_IMAGE",
	"vector-analyzer": "VECTOR_ANALYZER_IMAGE",
}

type phase string

const (
	phaseIdle       phase = "idle"
	phasePulling    phase = "pulling"
	phaseRecreating phase = "recreating"
	phaseCompleted  phase = "completed"
	phaseFailed     phase = "failed"
)

func (p phase) inProgress() bool {
	return p == phasePulling || p == phaseRecreating
}

type updateRequest struct {
	Service string `json:"service"`
	Image   string `json:"image"`
}

type triggerResponse struct {
	Status   string `json:"status"`
	UpdateId string `json:"updateId,omitempty"`
	Error    string `json:"error,omitempty"`
}

type statusResponse struct {
	Service     string `json:"service"`
	Phase       phase  `json:"phase"`
	UpdateId    string `json:"updateId,omitempty"`
	StartedAt   string `json:"startedAt,omitempty"`
	TargetImage string `json:"targetImage,omitempty"`
	Error       string `json:"error,omitempty"`
}

type serviceState struct {
	UpdateId    string
	Phase       phase
	StartedAt   time.Time
	TargetImage string
	Error       string
}

var (
	stateMu sync.RWMutex
	state   = map[string]*serviceState{}
)

func main() {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"ok"}`))
	})
	mux.Handle("/update", requireAuth(http.HandlerFunc(handleUpdate)))
	mux.Handle("/status/", requireAuth(http.HandlerFunc(handleStatus)))

	log.Println("vector-updater listening on :8080")
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
		writeTrigger(w, http.StatusBadRequest, triggerResponse{Status: "error", Error: "invalid json"})
		return
	}
	if alias, ok := serviceAliases[req.Service]; ok {
		req.Service = alias
	}
	if !allowedServices[req.Service] {
		writeTrigger(w, http.StatusForbidden, triggerResponse{Status: "error", Error: "service not in allowlist: " + req.Service})
		return
	}
	if req.Image == "" {
		writeTrigger(w, http.StatusBadRequest, triggerResponse{Status: "error", Error: "image is required"})
		return
	}

	stateMu.Lock()
	if existing, ok := state[req.Service]; ok && existing.Phase.inProgress() {
		stateMu.Unlock()
		writeTrigger(w, http.StatusConflict, triggerResponse{Status: "error", UpdateId: existing.UpdateId, Error: "update already in progress for " + req.Service})
		return
	}
	id := newUpdateId()
	state[req.Service] = &serviceState{
		UpdateId:    id,
		Phase:       phasePulling,
		StartedAt:   time.Now().UTC(),
		TargetImage: req.Image,
	}
	stateMu.Unlock()

	go runUpdate(req.Service, req.Image, id)

	writeTrigger(w, http.StatusAccepted, triggerResponse{Status: "triggered", UpdateId: id})
}

func handleStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "GET only", http.StatusMethodNotAllowed)
		return
	}
	service := strings.TrimPrefix(r.URL.Path, "/status/")
	if service == "" {
		http.Error(w, "service required", http.StatusBadRequest)
		return
	}

	stateMu.RLock()
	s, ok := state[service]
	stateMu.RUnlock()

	resp := statusResponse{Service: service, Phase: phaseIdle}
	if ok {
		resp.Phase = s.Phase
		resp.UpdateId = s.UpdateId
		resp.TargetImage = s.TargetImage
		resp.Error = s.Error
		if !s.StartedAt.IsZero() {
			resp.StartedAt = s.StartedAt.Format(time.RFC3339)
		}
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
}

func runUpdate(service, image, updateId string) {
	log.Printf("[%s] pulling %s", updateId, image)
	pullCtx, cancelPull := context.WithTimeout(context.Background(), pullTimeout)
	defer cancelPull()
	if out, err := exec.CommandContext(pullCtx, "docker", "pull", image).CombinedOutput(); err != nil {
		msg := strings.TrimSpace(string(out))
		if pullCtx.Err() == context.DeadlineExceeded {
			msg = "pull timed out after " + pullTimeout.String()
		}
		log.Printf("[%s] pull failed: %s", updateId, msg)
		setPhase(service, updateId, phaseFailed, "pull failed: "+msg)
		return
	}

	setPhase(service, updateId, phaseRecreating, "")
	log.Printf("[%s] recreating service %s", updateId, service)
	recreateCtx, cancelRecreate := context.WithTimeout(context.Background(), recreateTimeout)
	defer cancelRecreate()
	cmd := exec.CommandContext(recreateCtx, "docker", "compose", "--env-file", "/workspace/.env", "-f", "/workspace/docker-compose.yml", "up", "-d", "--force-recreate", "--no-deps", service)
	cmd.Env = append(os.Environ(), imageEnvByService[service]+"="+image)
	if out, err := cmd.CombinedOutput(); err != nil {
		msg := strings.TrimSpace(string(out))
		if recreateCtx.Err() == context.DeadlineExceeded {
			msg = "recreate timed out after " + recreateTimeout.String()
		}
		log.Printf("[%s] recreate failed: %s", updateId, msg)
		setPhase(service, updateId, phaseFailed, "recreate failed: "+msg)
		return
	}

	pruneOldImages(image)
	setPhase(service, updateId, phaseCompleted, "")
	log.Printf("[%s] completed", updateId)
}

func setPhase(service, updateId string, p phase, errMsg string) {
	stateMu.Lock()
	defer stateMu.Unlock()
	s, ok := state[service]
	if !ok || s.UpdateId != updateId {
		// State was replaced by a newer /update before this terminal phase landed.
		// Don't silently drop terminal results — log them so the operator and the
		// poller can distinguish "first run succeeded but was superseded" from
		// "first run never reported anything".
		if p == phaseCompleted || p == phaseFailed {
			log.Printf("[%s] terminal phase=%s for superseded run on service=%s (err=%q)",
				updateId, p, service, errMsg)
		}
		return
	}
	s.Phase = p
	if errMsg != "" {
		s.Error = errMsg
	}
}

func newUpdateId() string {
	buf := make([]byte, 8)
	if _, err := rand.Read(buf); err != nil {
		return strings.ReplaceAll(time.Now().UTC().Format("20060102T150405.000"), ".", "")
	}
	return hex.EncodeToString(buf)
}

// pruneOldImages removes old git-* tagged images for the given repo,
// keeping only the newest keepImageTags entries. Other tags (e.g. :latest) are left alone.
//
// Sorts by RFC3339 .Created from `docker inspect` — the prior string-sort on
// docker's human-readable {{.CreatedAt}} was lexicographic, which mis-orders
// images across day/month/TZ boundaries and could rmi the just-deployed image.
func pruneOldImages(image string) {
	repo := strings.SplitN(image, ":", 2)[0]
	if repo == "" {
		return
	}

	out, err := exec.Command(
		"docker", "images",
		"--format", "{{.ID}}|{{.Repository}}:{{.Tag}}",
		repo,
	).Output()
	if err != nil {
		log.Printf("prune: list failed for %s: %v", repo, err)
		return
	}

	type entry struct {
		id      string
		ref     string
		created time.Time
	}
	var candidates []entry
	for _, line := range strings.Split(strings.TrimSpace(string(out)), "\n") {
		if line == "" {
			continue
		}
		parts := strings.SplitN(line, "|", 2)
		if len(parts) != 2 {
			continue
		}
		tag := strings.TrimPrefix(parts[1], repo+":")
		if !strings.HasPrefix(tag, "git-") {
			continue
		}
		createdRaw, err := exec.Command("docker", "inspect", "--format", "{{.Created}}", parts[0]).Output()
		if err != nil {
			log.Printf("prune: inspect %s failed: %v", parts[0], err)
			continue
		}
		created, err := time.Parse(time.RFC3339Nano, strings.TrimSpace(string(createdRaw)))
		if err != nil {
			log.Printf("prune: parse created for %s failed: %v", parts[0], err)
			continue
		}
		candidates = append(candidates, entry{id: parts[0], ref: parts[1], created: created})
	}

	if len(candidates) <= keepImageTags {
		return
	}

	sort.Slice(candidates, func(i, j int) bool {
		return candidates[i].created.After(candidates[j].created)
	})

	for _, e := range candidates[keepImageTags:] {
		log.Printf("prune: removing %s (%s)", e.ref, e.id)
		if out, err := exec.Command("docker", "rmi", e.ref).CombinedOutput(); err != nil {
			log.Printf("prune: rmi %s failed: %s", e.ref, strings.TrimSpace(string(out)))
		}
	}
}

func writeTrigger(w http.ResponseWriter, status int, body triggerResponse) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
