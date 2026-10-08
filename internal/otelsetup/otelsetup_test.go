package otelsetup

import (
	"context"
	"testing"
)

// Fails with "conflicting Schema URL" when the semconv import drifts from the
// version resource.Default() uses after an SDK bump.
func TestSetup(t *testing.T) {
	t.Setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "")
	res, err := Setup(context.Background(), "test")
	if err != nil {
		t.Fatal(err)
	}
	_ = res.Shutdown(context.Background())
}
