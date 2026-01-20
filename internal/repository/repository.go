package repository

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/spongepowered/systemofadownload/internal/db"
)

// Reads are safe outside a transaction.
type Reads interface {
	GetArtifactByGroupAndId(ctx context.Context, arg db.GetArtifactByGroupAndIdParams) (db.Artifact, error)
	GetArtifactVersion(ctx context.Context, arg db.GetArtifactVersionParams) (db.ArtifactVersion, error)
	GetGroup(ctx context.Context, mavenID string) (db.Group, error)
	GroupExistsByMavenID(ctx context.Context, lower string) (bool, error)
	ListArtifactVersionAssets(ctx context.Context, artifactVersionID int64) ([]db.ArtifactVersionedAsset, error)
	ListArtifactVersionTags(ctx context.Context, artifactVersionID int64) ([]db.ArtifactVersionedTag, error)
	ListArtifactVersions(ctx context.Context, arg db.ListArtifactVersionsParams) ([]db.ArtifactVersion, error)
	ListArtifactsByGroup(ctx context.Context, groupID string) ([]db.Artifact, error)
	ListGroups(ctx context.Context) ([]db.Group, error)
}

// Writes must happen in a transaction.
type Writes interface {
	CreateArtifact(ctx context.Context, arg db.CreateArtifactParams) (db.Artifact, error)
	CreateArtifactVersion(ctx context.Context, arg db.CreateArtifactVersionParams) (db.ArtifactVersion, error)
	CreateArtifactVersionAsset(ctx context.Context, arg db.CreateArtifactVersionAssetParams) (db.ArtifactVersionedAsset, error)
	CreateArtifactVersionTag(ctx context.Context, arg db.CreateArtifactVersionTagParams) (db.ArtifactVersionedTag, error)
	CreateGroup(ctx context.Context, arg db.CreateGroupParams) (db.Group, error)
	UpdateArtifactVersionOrder(ctx context.Context, arg db.UpdateArtifactVersionOrderParams) error
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

// postgresRepository implements Repository using pgxpool.
type postgresRepository struct {
	pool *pgxpool.Pool
	q    db.Querier
}

// NewRepository creates a new Repository backed by a pgxpool connection pool.
func NewRepository(pool *pgxpool.Pool) Repository {
	return &postgresRepository{
		pool: pool,
		q:    db.New(pool),
	}
}

// WithTx executes fn within a transaction.
func (r *postgresRepository) WithTx(ctx context.Context, fn func(Tx) error) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}

	txQuerier := db.New(tx)

	if err := fn(txQuerier); err != nil {
		_ = tx.Rollback(ctx)
		return err
	}

	return tx.Commit(ctx)
}

// Delegate all Reads methods to the underlying querier
func (r *postgresRepository) GetArtifactByGroupAndId(ctx context.Context, arg db.GetArtifactByGroupAndIdParams) (db.Artifact, error) {
	return r.q.GetArtifactByGroupAndId(ctx, arg)
}

func (r *postgresRepository) GetArtifactVersion(ctx context.Context, arg db.GetArtifactVersionParams) (db.ArtifactVersion, error) {
	return r.q.GetArtifactVersion(ctx, arg)
}

func (r *postgresRepository) GetGroup(ctx context.Context, mavenID string) (db.Group, error) {
	return r.q.GetGroup(ctx, mavenID)
}

func (r *postgresRepository) GroupExistsByMavenID(ctx context.Context, lower string) (bool, error) {
	return r.q.GroupExistsByMavenID(ctx, lower)
}

func (r *postgresRepository) ListArtifactVersionAssets(ctx context.Context, artifactVersionID int64) ([]db.ArtifactVersionedAsset, error) {
	return r.q.ListArtifactVersionAssets(ctx, artifactVersionID)
}

func (r *postgresRepository) ListArtifactVersionTags(ctx context.Context, artifactVersionID int64) ([]db.ArtifactVersionedTag, error) {
	return r.q.ListArtifactVersionTags(ctx, artifactVersionID)
}

func (r *postgresRepository) ListArtifactVersions(ctx context.Context, arg db.ListArtifactVersionsParams) ([]db.ArtifactVersion, error) {
	return r.q.ListArtifactVersions(ctx, arg)
}

func (r *postgresRepository) ListArtifactsByGroup(ctx context.Context, groupID string) ([]db.Artifact, error) {
	return r.q.ListArtifactsByGroup(ctx, groupID)
}

func (r *postgresRepository) ListGroups(ctx context.Context) ([]db.Group, error) {
	return r.q.ListGroups(ctx)
}

