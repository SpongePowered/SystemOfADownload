package repository

import (
	"context"
	"fmt"
	"strconv"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/spongepowered/systemofadownload/internal/db"
)

// VersionQueryParams holds parameters for the paginated, filterable version listing.
type VersionQueryParams struct {
	GroupID     string
	ArtifactID  string
	Recommended *bool
	Tags        map[string]string // tag_key -> tag_value, all must match
	Limit       int32
	Offset      int32
}

// Reads are safe outside a transaction.
type Reads interface {
	GetArtifactByGroupAndId(ctx context.Context, arg db.GetArtifactByGroupAndIdParams) (db.Artifact, error)
	GetArtifactVersion(ctx context.Context, arg db.GetArtifactVersionParams) (db.ArtifactVersion, error)
	GetArtifactVersionByID(ctx context.Context, id int64) (db.ArtifactVersion, error)
	GetArtifactVersionSchema(ctx context.Context, arg db.GetArtifactVersionSchemaParams) ([]byte, error)
	GetGroup(ctx context.Context, mavenID string) (db.Group, error)
	GetPreviousVersion(ctx context.Context, arg db.GetPreviousVersionParams) (db.ArtifactVersion, error)
	GroupExistsByMavenID(ctx context.Context, lower string) (bool, error)
	IsVersionEnriched(ctx context.Context, id int64) (bool, error)
	ListArtifactVersionAssets(ctx context.Context, artifactVersionID int64) ([]db.ArtifactVersionedAsset, error)
	ListArtifactVersionTags(ctx context.Context, artifactVersionID int64) ([]db.ArtifactVersionedTag, error)
	ListArtifactVersions(ctx context.Context, arg db.ListArtifactVersionsParams) ([]db.ArtifactVersion, error)
	ListArtifactVersionStringsByArtifactID(ctx context.Context, arg db.ListArtifactVersionStringsByArtifactIDParams) ([]string, error)
	ListArtifactsByGroup(ctx context.Context, groupID string) ([]db.Artifact, error)
	ListDistinctTagsByArtifact(ctx context.Context, arg db.ListDistinctTagsByArtifactParams) ([]db.ListDistinctTagsByArtifactRow, error)
	ListGroups(ctx context.Context) ([]db.Group, error)
	ListTagsForVersions(ctx context.Context, versionIDs []int64) ([]db.ArtifactVersionedTag, error)
	ListVersionsFiltered(ctx context.Context, params VersionQueryParams) ([]db.ArtifactVersion, error)
	CountVersionsFiltered(ctx context.Context, params VersionQueryParams) (int, error)
	ListVersionsNeedingEnrichment(ctx context.Context, arg db.ListVersionsNeedingEnrichmentParams) ([]db.ArtifactVersion, error)
	ListVersionsNeedingChangelog(ctx context.Context, arg db.ListVersionsNeedingChangelogParams) ([]db.ArtifactVersion, error)
}

// Writes must happen in a transaction.
type Writes interface {
	CreateArtifact(ctx context.Context, arg db.CreateArtifactParams) (db.Artifact, error)
	CreateArtifactVersion(ctx context.Context, arg db.CreateArtifactVersionParams) (db.ArtifactVersion, error)
	CreateArtifactVersionAsset(ctx context.Context, arg db.CreateArtifactVersionAssetParams) (db.ArtifactVersionedAsset, error)
	CreateArtifactVersionTag(ctx context.Context, arg db.CreateArtifactVersionTagParams) (db.ArtifactVersionedTag, error)
	CreateGroup(ctx context.Context, arg db.CreateGroupParams) (db.Group, error)
	DeleteArtifactVersionTags(ctx context.Context, artifactVersionID int64) error
	UpdateArtifactFields(ctx context.Context, arg db.UpdateArtifactFieldsParams) (db.Artifact, error)
	UpdateArtifactVersionCommitBody(ctx context.Context, arg db.UpdateArtifactVersionCommitBodyParams) error
	UpdateArtifactVersionOrder(ctx context.Context, arg db.UpdateArtifactVersionOrderParams) error
	UpdateArtifactVersionSchema(ctx context.Context, arg db.UpdateArtifactVersionSchemaParams) error
}

// Tx provides both read and write operations within a transaction.
type Tx interface {
	Reads
	Writes
}

// Repository provides database operations with transaction support.
type Repository interface {
	Reads
	// WithTx executes the given function within a transaction.
	// If fn returns an error, the transaction is rolled back.
	// If fn returns nil, the transaction is committed.
	WithTx(ctx context.Context, fn func(Tx) error) error
}

// querierWithConn wraps *db.Queries and a db.DBTX connection so that
// both sqlc-generated methods and raw SQL queries use the same connection
// (pool for normal reads, pgx.Tx within transactions).
type querierWithConn struct {
	*db.Queries
	conn db.DBTX
}

// postgresRepository implements Repository using pgxpool.
type postgresRepository struct {
	pool *pgxpool.Pool
	querierWithConn
}

// NewRepository creates a new Repository backed by a pgxpool connection pool.
func NewRepository(pool *pgxpool.Pool) Repository {
	return &postgresRepository{
		pool: pool,
		querierWithConn: querierWithConn{
			Queries: db.New(pool),
			conn:    pool,
		},
	}
}

// WithTx executes fn within a transaction.
func (r *postgresRepository) WithTx(ctx context.Context, fn func(Tx) error) (err error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin transaction: %w", err)
	}
	defer func() {
		if recovered := recover(); recovered != nil {
			rollbackErr := tx.Rollback(ctx)
			if recoveredErr, ok := recovered.(error); ok {
				err = fmt.Errorf("panic in transaction: %w", recoveredErr)
			} else {
				err = fmt.Errorf("panic in transaction: %v", recovered)
			}
			if rollbackErr != nil {
				err = fmt.Errorf("%w (rollback failed: %w)", err, rollbackErr)
			}
		}
	}()

	txQwc := &querierWithConn{
		Queries: db.New(tx),
		conn:    tx,
	}

	if err := fn(txQwc); err != nil {
		if rollbackErr := tx.Rollback(ctx); rollbackErr != nil {
			return fmt.Errorf("transaction failed: %w (rollback failed: %w)", err, rollbackErr)
		}
		return fmt.Errorf("transaction failed: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("commit transaction: %w", err)
	}
	return nil
}

// ListTagsForVersions delegates to the sqlc-generated method.
func (q *querierWithConn) ListTagsForVersions(ctx context.Context, versionIDs []int64) ([]db.ArtifactVersionedTag, error) {
	return q.Queries.ListTagsForVersions(ctx, versionIDs)
}

// ListVersionsFiltered returns paginated versions, optionally filtered by tags and recommended status.
// When tags are provided, uses a dynamic SQL query with a subquery to match all tags.
func (q *querierWithConn) ListVersionsFiltered(ctx context.Context, params VersionQueryParams) ([]db.ArtifactVersion, error) {
	if len(params.Tags) == 0 {
		return q.ListArtifactVersionsPaginated(ctx, db.ListArtifactVersionsPaginatedParams{
			GroupID:     params.GroupID,
			ArtifactID:  params.ArtifactID,
			Recommended: params.Recommended,
			Limit:       params.Limit,
			Offset:      params.Offset,
		})
	}

	// Build dynamic query for tag filtering using a subquery to match
	// versions that have ALL specified tags. The SELECT columns and Scan
	// fields must stay in sync with db.ArtifactVersion (see internal/db/models.go).
	var args []any
	argN := 1

	query := `SELECT av.id, av.artifact_id, av.version, av.sort_order, av.recommended, av.commit_body
FROM artifact_versions av
JOIN artifacts a ON av.artifact_id = a.id
WHERE a.group_id = $` + strconv.Itoa(argN)
	args = append(args, params.GroupID)
	argN++

	query += ` AND a.artifact_id = $` + strconv.Itoa(argN)
	args = append(args, params.ArtifactID)
	argN++

	if params.Recommended != nil {
		query += ` AND av.recommended = $` + strconv.Itoa(argN)
		args = append(args, *params.Recommended)
		argN++
	}

	// Subquery: find version IDs that match ALL provided tags.
	// Supports prefix matching with dot boundary: "minecraft:1.12" matches "1.12" and
	// "1.12.2" but NOT "1.120". The value must match exactly or be a prefix followed by a dot.
	query += ` AND av.id IN (SELECT t.artifact_version_id FROM artifact_versioned_tags t WHERE (`
	i := 0
	for key, value := range params.Tags {
		if i > 0 {
			query += ` OR `
		}
		query += `(t.tag_key = $` + strconv.Itoa(argN) +
			` AND (t.tag_value = $` + strconv.Itoa(argN+1) +
			` OR t.tag_value LIKE $` + strconv.Itoa(argN+2) + `))`
		args = append(args, key, value, value+".%")
		argN += 3
		i++
	}
	query += `) GROUP BY t.artifact_version_id HAVING COUNT(DISTINCT t.tag_key) = $` + strconv.Itoa(argN) + `)`
	args = append(args, len(params.Tags))
	argN++

	query += ` ORDER BY av.sort_order DESC`
	query += ` LIMIT $` + strconv.Itoa(argN)
	args = append(args, params.Limit)
	argN++
	query += ` OFFSET $` + strconv.Itoa(argN)
	args = append(args, params.Offset)

	rows, err := q.conn.Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("listing filtered versions: %w", err)
	}
	defer rows.Close()

	var versions []db.ArtifactVersion
	for rows.Next() {
		var v db.ArtifactVersion
		if err := rows.Scan(&v.ID, &v.ArtifactID, &v.Version, &v.SortOrder, &v.Recommended, &v.CommitBody); err != nil {
			return nil, fmt.Errorf("scanning version row: %w", err)
		}
		versions = append(versions, v)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterating filtered version rows: %w", err)
	}
	return versions, nil
}

// CountVersionsFiltered returns the total count of versions matching the filter criteria
// (without LIMIT/OFFSET), for pagination support.
func (q *querierWithConn) CountVersionsFiltered(ctx context.Context, params VersionQueryParams) (int, error) {
	if len(params.Tags) == 0 {
		count, err := q.CountArtifactVersions(ctx, db.CountArtifactVersionsParams{
			GroupID:     params.GroupID,
			ArtifactID:  params.ArtifactID,
			Recommended: params.Recommended,
		})
		if err != nil {
			return 0, err
		}
		return int(count), nil
	}

	// Dynamic count query with tag filtering
	var args []any
	argN := 1

	query := `SELECT COUNT(*) FROM artifact_versions av
JOIN artifacts a ON av.artifact_id = a.id
WHERE a.group_id = $` + strconv.Itoa(argN)
	args = append(args, params.GroupID)
	argN++

	query += ` AND a.artifact_id = $` + strconv.Itoa(argN)
	args = append(args, params.ArtifactID)
	argN++

	if params.Recommended != nil {
		query += ` AND av.recommended = $` + strconv.Itoa(argN)
		args = append(args, *params.Recommended)
		argN++
	}

	query += ` AND av.id IN (SELECT t.artifact_version_id FROM artifact_versioned_tags t WHERE (`
	i := 0
	for key, value := range params.Tags {
		if i > 0 {
			query += ` OR `
		}
		query += `(t.tag_key = $` + strconv.Itoa(argN) +
			` AND (t.tag_value = $` + strconv.Itoa(argN+1) +
			` OR t.tag_value LIKE $` + strconv.Itoa(argN+2) + `))`
		args = append(args, key, value, value+".%")
		argN += 3
		i++
	}
	query += `) GROUP BY t.artifact_version_id HAVING COUNT(DISTINCT t.tag_key) = $` + strconv.Itoa(argN) + `)`
	args = append(args, len(params.Tags))

	var count int
	err := q.conn.QueryRow(ctx, query, args...).Scan(&count)
	if err != nil {
		return 0, fmt.Errorf("counting filtered versions: %w", err)
	}
	return count, nil
}
