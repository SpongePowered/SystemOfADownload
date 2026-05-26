// Package dbpool centralizes pgxpool construction so every binary gets the
// same connection sizing, lifetime, and tracing setup.
package dbpool

import (
	"context"
	"fmt"
	"time"

	"github.com/exaring/otelpgx"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Open parses databaseURL, applies SOAD's pool defaults (warm minimum,
// jittered lifetime, otelpgx tracer), and returns a ready pool. The
// context bounds connection establishment; callers should pass a deadline.
//
// pgxpool's out-of-the-box MinConns=0 means idle connections get reaped and
// every request pays for a fresh TCP+TLS+SCRAM handshake (~20-60ms on staging).
// Setting MinConns keeps a warm floor so pool.acquire stays in the microsecond
// range. URL query params (pool_min_conns, pool_max_conns, etc.) still win.
func Open(ctx context.Context, databaseURL string) (*pgxpool.Pool, error) {
	cfg, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		return nil, fmt.Errorf("parsing database url: %w", err)
	}

	cfg.ConnConfig.Tracer = otelpgx.NewTracer()

	// Only override when the URL didn't specify a value (pgxpool's zero-ish
	// defaults: MinConns=0, MaxConnLifetimeJitter=0, HealthCheckPeriod=1m).
	if cfg.MinConns == 0 {
		cfg.MinConns = 2
	}
	if cfg.MaxConnLifetimeJitter == 0 {
		cfg.MaxConnLifetimeJitter = 5 * time.Minute
	}
	if cfg.HealthCheckPeriod >= time.Minute {
		cfg.HealthCheckPeriod = 30 * time.Second
	}

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("creating pool: %w", err)
	}
	return pool, nil
}
