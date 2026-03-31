package otelsetup

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"time"

	promexporter "go.opentelemetry.io/otel/exporters/prometheus"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetrichttp"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	"go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.40.0"

	"github.com/prometheus/client_golang/prometheus/promhttp"
)

// Result holds the outputs of Setup.
type Result struct {
	// Shutdown flushes and shuts down all OTel providers.
	Shutdown func(context.Context) error
	// MetricsHandler serves Prometheus metrics at /metrics.
	// Always non-nil — metrics work even without OTLP.
	MetricsHandler http.Handler
}

// Setup initializes OpenTelemetry with a Prometheus metrics exporter (always)
// and OTLP trace/metric exporters (when OTEL_EXPORTER_OTLP_ENDPOINT is set).
//
// Prometheus metrics are pull-based and require no collector. The returned
// MetricsHandler should be registered on an HTTP mux at /metrics.
func Setup(ctx context.Context, serviceName string) (*Result, error) {
	var shutdownFuncs []func(context.Context) error

	shutdown := func(ctx context.Context) error {
		var errs []error
		for _, fn := range shutdownFuncs {
			if e := fn(ctx); e != nil {
				errs = append(errs, e)
			}
		}
		return errors.Join(errs...)
	}

	res, err := resource.Merge(
		resource.Default(),
		resource.NewWithAttributes(
			semconv.SchemaURL,
			semconv.ServiceNameKey.String(serviceName),
		),
	)
	if err != nil {
		return nil, err
	}

	// Prometheus exporter (always active — pull-based, no collector needed)
	promExp, err := promexporter.New()
	if err != nil {
		return nil, err
	}
	readers := []metric.Option{
		metric.WithReader(promExp),
	}

	// OTLP exporters (only when endpoint is configured)
	otlpEnabled := os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT") != ""
	if otlpEnabled {
		otlpCtx, otlpCancel := context.WithTimeout(ctx, 10*time.Second)
		defer otlpCancel()
		traceExporter, err := otlptracehttp.New(otlpCtx)
		if err != nil {
			return nil, err
		}
		tp := trace.NewTracerProvider(
			trace.WithBatcher(traceExporter),
			trace.WithResource(res),
		)
		shutdownFuncs = append(shutdownFuncs, tp.Shutdown)
		otel.SetTracerProvider(tp)
		otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
			propagation.TraceContext{},
			propagation.Baggage{},
		))

		metricExporter, err := otlpmetrichttp.New(otlpCtx)
		if err != nil {
			return &Result{Shutdown: shutdown, MetricsHandler: promhttp.Handler()}, err
		}
		readers = append(readers, metric.WithReader(
			metric.NewPeriodicReader(metricExporter, metric.WithInterval(30*time.Second)),
		))

		slog.Info("OpenTelemetry initialized with OTLP exporters", "service", serviceName)
	} else {
		slog.Info("OpenTelemetry metrics enabled (Prometheus only, no OTLP)", "service", serviceName)
	}

	// MeterProvider with all readers (Prometheus always, OTLP optionally)
	mp := metric.NewMeterProvider(
		append(readers, metric.WithResource(res))...,
	)
	shutdownFuncs = append(shutdownFuncs, mp.Shutdown)
	otel.SetMeterProvider(mp)

	return &Result{
		Shutdown:       shutdown,
		MetricsHandler: promhttp.Handler(),
	}, nil
}
