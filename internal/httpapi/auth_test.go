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
		mw := AdminAuthMiddleware("secret123")
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
		mw := AdminAuthMiddleware("secret123")
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

	t.Run("allows write operations with valid token", func(t *testing.T) {
		mw := AdminAuthMiddleware("secret123")
		handler := mw(noopHandler, "RegisterArtifact")

		r := httptest.NewRequest("POST", "/groups/org/artifacts", http.NoBody)
		r.Header.Set("Authorization", "Bearer secret123")
		w := httptest.NewRecorder()

		result, err := handler(context.Background(), w, r, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if result != "ok" {
			t.Error("expected handler to execute")
		}
	})

	t.Run("rejects wrong token", func(t *testing.T) {
		mw := AdminAuthMiddleware("secret123")
		handler := mw(noopHandler, "PutArtifactSchema")

		r := httptest.NewRequest("PUT", "/schema", http.NoBody)
		r.Header.Set("Authorization", "Bearer wrong-token")
		w := httptest.NewRecorder()

		result, _ := handler(context.Background(), w, r, nil)
		if result != nil {
			t.Error("expected handler to be blocked")
		}
		if w.Code != http.StatusUnauthorized {
			t.Errorf("expected 401, got %d", w.Code)
		}
	})

	t.Run("empty token disables auth entirely", func(t *testing.T) {
		mw := AdminAuthMiddleware("")
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

	t.Run("covers all write operations", func(t *testing.T) {
		mw := AdminAuthMiddleware("token")
		for _, op := range []string{"RegisterGroup", "RegisterArtifact", "UpdateArtifact", "PutArtifactSchema"} {
			handler := mw(noopHandler, op)
			r := httptest.NewRequest("POST", "/", http.NoBody)
			w := httptest.NewRecorder()
			result, _ := handler(context.Background(), w, r, nil)
			if result != nil {
				t.Errorf("operation %q should require auth", op)
			}
		}
	})
}
