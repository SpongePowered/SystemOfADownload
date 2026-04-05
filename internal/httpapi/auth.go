package httpapi

import (
	"context"
	"net/http"
	"strings"

	"github.com/spongepowered/systemofadownload/api"
)

// scope names
const (
	ScopeAdmin = "admin"
	ScopeSync  = "sync"
)

// operationScope maps each write operationID to the minimum scope required.
var operationScope = map[string]string{
	"RegisterGroup":     ScopeAdmin,
	"RegisterArtifact":  ScopeAdmin,
	"UpdateArtifact":    ScopeAdmin,
	"PutArtifactSchema": ScopeAdmin,
	"TriggerSync":       ScopeSync,
}

// scopeIncludes returns true if `has` is a superset of (or equal to) `need`.
// admin ⊃ sync.
func scopeIncludes(has, need string) bool {
	if has == need {
		return true
	}
	return has == ScopeAdmin
}

// TokenSet maps bearer tokens to their scope.
type TokenSet struct {
	tokens map[string]string // token → scope
}

// NewTokenSet builds a TokenSet from admin and sync token lists.
func NewTokenSet(adminTokens, syncTokens []string) *TokenSet {
	ts := &TokenSet{tokens: make(map[string]string, len(adminTokens)+len(syncTokens))}
	for _, t := range adminTokens {
		if t != "" {
			ts.tokens[t] = ScopeAdmin
		}
	}
	for _, t := range syncTokens {
		if t != "" {
			// don't downgrade an admin token that also appears in the sync list
			if _, exists := ts.tokens[t]; !exists {
				ts.tokens[t] = ScopeSync
			}
		}
	}
	return ts
}

// AdminAuthMiddleware returns a strict middleware that enforces scoped Bearer
// token authentication on write operations. Read operations pass through.
//
// If the TokenSet is empty, all operations are allowed (auth disabled).
func AdminAuthMiddleware(ts *TokenSet) api.StrictMiddlewareFunc {
	return func(f api.StrictHandlerFunc, operationID string) api.StrictHandlerFunc {
		requiredScope, isWrite := operationScope[operationID]
		if ts == nil || len(ts.tokens) == 0 || !isWrite {
			return f
		}

		return func(ctx context.Context, w http.ResponseWriter, r *http.Request, request interface{}) (interface{}, error) {
			auth := r.Header.Get("Authorization")
			if !strings.HasPrefix(auth, "Bearer ") {
				w.Header().Set("WWW-Authenticate", `Bearer realm="admin"`)
				http.Error(w, "unauthorized", http.StatusUnauthorized)
				return nil, nil
			}

			bearer := strings.TrimPrefix(auth, "Bearer ")
			scope, known := ts.tokens[bearer]
			if !known {
				w.Header().Set("WWW-Authenticate", `Bearer realm="admin"`)
				http.Error(w, "unauthorized", http.StatusUnauthorized)
				return nil, nil
			}

			if !scopeIncludes(scope, requiredScope) {
				http.Error(w, "forbidden", http.StatusForbidden)
				return nil, nil
			}

			return f(ctx, w, r, request)
		}
	}
}
