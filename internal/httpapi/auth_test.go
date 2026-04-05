package httpapi

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestAdminAuthMiddleware(t *testing.T) {
	t.Parallel()

	noopHandler := func(ctx context.Context, w http.ResponseWriter, r *http.Request, request interface{}) (interface{}, error) {
		return "ok", nil
	}

	t.Run("allows read operations without token", func(t *testing.T) {
		ts := NewTokenSet([]string{"secret123"}, nil)
		mw := AdminAuthMiddleware(ts)
		handler := mw(noopHandler, "GetGroups")

		r := httptest.NewRequest("GET", "/groups", http.NoBody)
		w := httptest.NewRecorder()

		result, err := handler(context.Background(), w, r, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if result != "ok" {
			t.Error("expected handler to execute")
		}
	})

	t.Run("blocks write operations without token", func(t *testing.T) {
		ts := NewTokenSet([]string{"secret123"}, nil)
		mw := AdminAuthMiddleware(ts)
		handler := mw(noopHandler, "RegisterArtifact")

		r := httptest.NewRequest("POST", "/groups/org/artifacts", http.NoBody)
		w := httptest.NewRecorder()

		result, _ := handler(context.Background(), w, r, nil)
		if result != nil {
			t.Error("expected handler to be blocked")
		}
		if w.Code != http.StatusUnauthorized {
			t.Errorf("expected 401, got %d", w.Code)
		}
		if w.Header().Get("WWW-Authenticate") == "" {
			t.Error("expected WWW-Authenticate header")
		}
	})

	t.Run("admin token allows any write operation", func(t *testing.T) {
		ts := NewTokenSet([]string{"admin-tok"}, nil)
		mw := AdminAuthMiddleware(ts)

		for _, op := range []string{"RegisterGroup", "RegisterArtifact", "UpdateArtifact", "PutArtifactSchema", "TriggerSync"} {
			handler := mw(noopHandler, op)
			r := httptest.NewRequest("POST", "/", http.NoBody)
			r.Header.Set("Authorization", "Bearer admin-tok")
			w := httptest.NewRecorder()

			result, err := handler(context.Background(), w, r, nil)
			if err != nil {
				t.Fatalf("unexpected error for %q: %v", op, err)
			}
			if result != "ok" {
				t.Errorf("admin token should be allowed for %q", op)
			}
		}
	})

	t.Run("sync token allows TriggerSync", func(t *testing.T) {
		ts := NewTokenSet(nil, []string{"ci-tok"})
		mw := AdminAuthMiddleware(ts)
		handler := mw(noopHandler, "TriggerSync")

		r := httptest.NewRequest("POST", "/sync", http.NoBody)
		r.Header.Set("Authorization", "Bearer ci-tok")
		w := httptest.NewRecorder()

		result, err := handler(context.Background(), w, r, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if result != "ok" {
			t.Error("sync token should be allowed for TriggerSync")
		}
	})

	t.Run("sync token is forbidden from admin operations", func(t *testing.T) {
		ts := NewTokenSet(nil, []string{"ci-tok"})
		mw := AdminAuthMiddleware(ts)

		for _, op := range []string{"RegisterGroup", "RegisterArtifact", "UpdateArtifact", "PutArtifactSchema"} {
			handler := mw(noopHandler, op)
			r := httptest.NewRequest("POST", "/", http.NoBody)
			r.Header.Set("Authorization", "Bearer ci-tok")
			w := httptest.NewRecorder()

			result, _ := handler(context.Background(), w, r, nil)
			if result != nil {
				t.Errorf("sync token should be forbidden for %q", op)
			}
			if w.Code != http.StatusForbidden {
				t.Errorf("expected 403 for %q, got %d", op, w.Code)
			}
		}
	})

	t.Run("unknown token returns 401", func(t *testing.T) {
		ts := NewTokenSet([]string{"admin-tok"}, []string{"ci-tok"})
		mw := AdminAuthMiddleware(ts)
		handler := mw(noopHandler, "RegisterArtifact")

		r := httptest.NewRequest("POST", "/", http.NoBody)
		r.Header.Set("Authorization", "Bearer unknown")
		w := httptest.NewRecorder()

		result, _ := handler(context.Background(), w, r, nil)
		if result != nil {
			t.Error("expected handler to be blocked")
		}
		if w.Code != http.StatusUnauthorized {
			t.Errorf("expected 401, got %d", w.Code)
		}
	})

	t.Run("nil token set disables auth", func(t *testing.T) {
		mw := AdminAuthMiddleware(nil)
		handler := mw(noopHandler, "RegisterArtifact")

		r := httptest.NewRequest("POST", "/groups/org/artifacts", http.NoBody)
		w := httptest.NewRecorder()

		result, err := handler(context.Background(), w, r, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if result != "ok" {
			t.Error("expected handler to execute when auth disabled")
		}
	})

	t.Run("empty token set disables auth", func(t *testing.T) {
		ts := NewTokenSet(nil, nil)
		mw := AdminAuthMiddleware(ts)
		handler := mw(noopHandler, "RegisterArtifact")

		r := httptest.NewRequest("POST", "/groups/org/artifacts", http.NoBody)
		w := httptest.NewRecorder()

		result, err := handler(context.Background(), w, r, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if result != "ok" {
			t.Error("expected handler to execute when auth disabled")
		}
	})

	t.Run("duplicate token in both lists keeps admin scope", func(t *testing.T) {
		ts := NewTokenSet([]string{"shared-tok"}, []string{"shared-tok"})
		mw := AdminAuthMiddleware(ts)
		handler := mw(noopHandler, "RegisterArtifact")

		r := httptest.NewRequest("POST", "/", http.NoBody)
		r.Header.Set("Authorization", "Bearer shared-tok")
		w := httptest.NewRecorder()

		result, err := handler(context.Background(), w, r, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if result != "ok" {
			t.Error("token in both lists should retain admin scope")
		}
	})
}
