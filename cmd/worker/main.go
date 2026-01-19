package main

import (
	"context"
	"fmt"
	"time"

	"go.uber.org/fx"
)

type Worker struct {
	ticker *time.Ticker
	stopCh chan struct{}
}

func NewWorker(lc fx.Lifecycle) *Worker {
	w := &Worker{
		ticker: time.NewTicker(1 * time.Minute),
		stopCh: make(chan struct{}),
	}

	lc.Append(fx.Hook{
		OnStart: func(ctx context.Context) error {
			fmt.Println("Worker starting...")
			go w.Run()
			return nil
		},
		OnStop: func(ctx context.Context) error {
			fmt.Println("Worker shutting down...")
			close(w.stopCh)
			w.ticker.Stop()
			return nil
		},
	})

	return w
}

func (w *Worker) Run() {
	for {
		select {
		case <-w.ticker.C:
			fmt.Println("Worker heart-beat")
		case <-w.stopCh:
			return
		}
	}
}

func main() {
	fx.New(
		fx.Provide(NewWorker),
		fx.Invoke(func(*Worker) {}),
	).Run()
}
