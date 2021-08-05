/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.synchronizer;

import akka.actor.Extension;
import akka.actor.typed.BackoffSupervisorStrategy;
import akka.actor.typed.SupervisorStrategy;
import com.typesafe.config.Config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class SynchronizerSettings implements Extension {

    public final ReactiveSync reactiveSync;
    public final Asset asset;
    public final VersionSync versionSync;

    public static final class Asset {
        public final int poolSize;
        public final int parallelism;
        public final Duration initialBackoff;
        public final Duration maximumBackoff;
        public final double backoffFactor;
        public final Duration timeout;

        public final BackoffSupervisorStrategy backoff;

        Asset(Config config) {
            this.poolSize = config.getInt("pool-size");
            this.initialBackoff = Duration.ofSeconds(config.getDuration("initial-backoff", TimeUnit.SECONDS));
            this.maximumBackoff = Duration.ofSeconds(config.getDuration("maximum-backoff", TimeUnit.SECONDS));
            this.backoffFactor = config.getDouble("backoff-factor");
            this.backoff = SupervisorStrategy.restartWithBackoff(
                this.initialBackoff, this.maximumBackoff, this.backoffFactor);
            this.parallelism = config.getInt("parallelism");
            this.timeout = Duration.ofMinutes(config.getDuration("time-out", TimeUnit.MINUTES));
        }
    }

    public static final class VersionSync {
        public final int versionSyncPoolSize;
        public final Duration interval;
        public final Duration startupDelay;

        VersionSync(Config config) {
            this.versionSyncPoolSize = config.getInt("pool-size");
            this.interval = Duration.ofSeconds(config.getDuration("interval", TimeUnit.SECONDS));
            this.startupDelay = Duration.ofSeconds(config.getDuration("delay", TimeUnit.SECONDS));
        }
    }

    public static final class ReactiveSync {
        public final int poolSize;
        public final int parallelism;
        public final Duration timeOut;

        ReactiveSync(Config config) {
            this.poolSize = config.getInt("pool-size");
            this.parallelism = config.getInt("parallelism");
            this.timeOut = Duration.ofMinutes(config.getDuration("time-out", TimeUnit.MINUTES));
        }
    }

    public SynchronizerSettings(Config config) {
        final var assetConfig = config.getConfig("asset");
        this.asset = new Asset(assetConfig);

        final var versionSync = config.getConfig("version-sync");
        this.versionSync = new VersionSync(versionSync);

        final var reactiveSync = config.getConfig("reactive-sync");
        this.reactiveSync = new ReactiveSync(reactiveSync);
    }
}
