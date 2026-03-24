package httpapi

import (
	"context"
	"net/http"
	"strings"

	"github.com/spongepowered/systemofadownload/api"
)

// writeOperations is the set of operationIDs that require authentication.
var writeOperations = map[string]bool{
	"RegisterGroup":    true,
	"RegisterArtifact": true,
	"UpdateArtifact":   true,
	"PutArtifactSchema": true,
}

// AdminAuthMiddleware returns a strict middleware that requires a Bearer token
// for write operations. Read operations pass through without authentication.
//
// If token is empty, all operations are allowed (auth disabled).
func AdminAuthMiddleware(token string) api.StrictMiddlewareFunc {
	return func(f api.StrictHandlerFunc, operationID string) api.StrictHandlerFunc {
		if token == "" || !writeOperations[operationID] {
			return f
		}

		return func(ctx context.Context, w http.ResponseWriter, r *http.Request, request interface{}) (interface{}, error) {
			auth := r.Header.Get("Authorization")
			if !strings.HasPrefix(auth, "Bearer ") || strings.TrimPrefix(auth, "Bearer ") != token {
				w.Header().Set("WWW-Authenticate", `Bearer realm="admin"`)
				http.Error(w, "unauthorized", http.StatusUnauthorized)
				return nil, nil
			}
			return f(ctx, w, r, request)
		}
	}
}
