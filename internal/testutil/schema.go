// Package testutil provides shared test helpers.
package testutil

import (
	"os"
	"path/filepath"
	"runtime"
	"testing"
)

// DBSchema returns the SQL schema from db/schema.sql.
// It uses runtime.Caller to locate the project root regardless of the
// test's working directory.
func DBSchema(t *testing.T) string {
	t.Helper()
	_, thisFile, _, _ := runtime.Caller(0)
	projectRoot := filepath.Join(filepath.Dir(thisFile), "..", "..")
	data, err := os.ReadFile(filepath.Join(projectRoot, "db", "schema.sql"))
	if err != nil {
		t.Fatalf("failed to read db/schema.sql: %v", err)
	}
	return string(data)
}
