package domain_test

import (
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"testing"

	"github.com/spongepowered/systemofadownload/internal/domain"
)

// authorResolutionPredicate matches the schema literal shared by the keyset
// query and the partial index that has to serve it.
var authorResolutionPredicate = regexp.MustCompile(
	`\(commit_body->'authorResolution'->>'schema'\)\s+IS DISTINCT FROM\s+'(\d+)'`,
)

// TestAuthorResolutionSchemaMatchesSQL keeps domain.AuthorResolutionSchema in
// lockstep with the SQL that depends on it.
//
// A partial index predicate cannot be parameterised, so the schema version is a
// literal in both ListVersionsNeedingGitHubAuthorResolution and
// idx_versions_github_authors_unresolved. Drift is silent and harmful in both
// directions:
//
//   - Bumping the constant without updating the SQL means versions carrying the
//     old schema are never selected, so the re-resolution never happens, while
//     already-current versions are selected on every tick and skipped, churning
//     the whole history every 2 minutes.
//   - Updating the query without a migration that replaces the index leaves the
//     predicates unable to imply one another, so the keyset scan quietly stops
//     being indexed.
func TestAuthorResolutionSchemaMatchesSQL(t *testing.T) {
	t.Parallel()

	want := strconv.Itoa(domain.AuthorResolutionSchema)
	// db/schema.sql mirrors the cumulative schema, so it stands in for the
	// current index definition. Applied migrations are immutable history and
	// are deliberately not checked.
	for _, name := range []string{"query.sql", "schema.sql"} {
		path := filepath.Join("..", "..", "db", name)
		content, err := os.ReadFile(path)
		if err != nil {
			t.Fatalf("reading %s: %v", path, err)
		}

		matches := authorResolutionPredicate.FindAllStringSubmatch(string(content), -1)
		if len(matches) == 0 {
			t.Fatalf("%s: author resolution schema predicate not found; "+
				"if the predicate changed shape, update authorResolutionPredicate", path)
		}
		for _, match := range matches {
			if match[1] != want {
				t.Errorf("%s: schema literal is '%s', want '%s' from "+
					"domain.AuthorResolutionSchema; bumping the constant also needs a "+
					"migration replacing idx_versions_github_authors_unresolved",
					path, match[1], want)
			}
		}
	}
}
