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
package org.spongepowered.synchronizer.actor;

import akka.actor.AbstractExtensionId;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.actor.ExtensionId;
import akka.actor.ExtensionIdProvider;
import com.typesafe.config.Config;

import java.time.Duration;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

public class ArtifactSyncExtension extends AbstractExtensionId<ArtifactSyncExtension.Settings>
    implements ExtensionIdProvider {

    public static final ArtifactSyncExtension SettingsProvider = new ArtifactSyncExtension();

    @Override
    public Settings createExtension(final ExtendedActorSystem system) {
        return new Settings(
            system.settings().config().getConfig("systemofadownload.synchronizer.worker.version-registration"));
    }


    @Override
    public ExtensionId<? extends Extension> lookup() {
        return SettingsProvider;
    }

    public static final class Settings implements Extension {

        public final int poolSize;
        public final int versionFanoutParallelism;
        public final int parallelism;
        public final Duration timeOut;
        public final Duration individualTimeOut;

        public Settings(Config config) {
            this.poolSize = config.getInt("pool-size");
            this.versionFanoutParallelism = config.getInt("fan-out-parallelism");
            this.parallelism = config.getInt("parallelism");
            this.timeOut = Duration.ofSeconds(config.getDuration("time-out", TimeUnit.SECONDS));
            this.individualTimeOut = Duration.ofSeconds(config.getDuration("registration-time-out", TimeUnit.SECONDS));

        }

        @Override
        public String toString() {
            return new StringJoiner(
                ", ", Settings.class.getSimpleName() + "[", "]")
                .add("poolSize=" + poolSize)
                .add("versionFanoutParallelism=" + versionFanoutParallelism)
                .add("parallelism=" + parallelism)
                .add("timeOut=" + timeOut)
                .add("individualTimeOut=" + individualTimeOut)
                .toString();
        }
    }
}
