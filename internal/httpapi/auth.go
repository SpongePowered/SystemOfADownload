package httpapi

import (
	"context"
	"net/http"
	"strings"

	"github.com/spongepowered/systemofadownload/api"
)

// writeOperations is the set of operationIDs that require authentication.
var writeOperations = map[string]bool{
	"RegisterGroup":     true,
	"RegisterArtifact":  true,
	"UpdateArtifact":    true,
	"PutArtifactSchema": true,
	"TriggerSync":       true,
}

// AdminAuthMiddleware returns a strict middleware that requires a Bearer token
// for write operations. Read operations pass through without authentication.
//
// If no tokens are provided, all operations are allowed (auth disabled).
func AdminAuthMiddleware(tokens []string) api.StrictMiddlewareFunc {
	allowed := make(map[string]struct{}, len(tokens))
	for _, t := range tokens {
		if t != "" {
			allowed[t] = struct{}{}
		}
	}

	return func(f api.StrictHandlerFunc, operationID string) api.StrictHandlerFunc {
		if len(allowed) == 0 || !writeOperations[operationID] {
			return f
		}

		return func(ctx context.Context, w http.ResponseWriter, r *http.Request, request interface{}) (interface{}, error) {
			auth := r.Header.Get("Authorization")
			bearer := strings.TrimPrefix(auth, "Bearer ")
			if !strings.HasPrefix(auth, "Bearer ") {
				bearer = ""
			}
			if _, ok := allowed[bearer]; !ok {
				w.Header().Set("WWW-Authenticate", `Bearer realm="admin"`)
				http.Error(w, "unauthorized", http.StatusUnauthorized)
				return nil, nil
			}
			return f(ctx, w, r, request)
		}
	}
}
