package frontend

import (
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"
)

// serverTiming collects named timing phases and writes them as a Server-Timing
// HTTP header. Safe for concurrent use from multiple goroutines.
type serverTiming struct {
	mu     sync.Mutex
	phases []timingPhase
}

type timingPhase struct {
	name  string
	desc  string
	start time.Time
	dur   time.Duration
}

type activePhase struct {
	t     *serverTiming
	index int
}

// start begins a new timing phase. Call end() on the returned activePhase when done.
func (t *serverTiming) start(name, desc string) activePhase {
	t.mu.Lock()
	defer t.mu.Unlock()
	idx := len(t.phases)
	t.phases = append(t.phases, timingPhase{name: name, desc: desc, start: time.Now()})
	return activePhase{t: t, index: idx}
}

// end records the duration for this phase.
func (a activePhase) end() {
	a.t.mu.Lock()
	defer a.t.mu.Unlock()
	a.t.phases[a.index].dur = time.Since(a.t.phases[a.index].start)
}

// write sets the Server-Timing header on the response. Must be called before
// writing the response body.
func (t *serverTiming) write(w http.ResponseWriter) {
	t.mu.Lock()
	defer t.mu.Unlock()
	if len(t.phases) == 0 {
		return
	}
	var parts []string
	for _, p := range t.phases {
		ms := float64(p.dur.Microseconds()) / 1000.0
		if p.desc != "" {
			parts = append(parts, fmt.Sprintf("%s;desc=%q;dur=%.1f", p.name, p.desc, ms))
		} else {
			parts = append(parts, fmt.Sprintf("%s;dur=%.1f", p.name, ms))
		}
	}
	w.Header().Set("Server-Timing", strings.Join(parts, ", "))
}

// String returns a human-readable summary of all phases (for logging).
func (t *serverTiming) String() string {
	t.mu.Lock()
	defer t.mu.Unlock()
	var parts []string
	for _, p := range t.phases {
		parts = append(parts, fmt.Sprintf("%s=%.1fms", p.name, float64(p.dur.Microseconds())/1000.0))
	}
	return strings.Join(parts, " ")
}
