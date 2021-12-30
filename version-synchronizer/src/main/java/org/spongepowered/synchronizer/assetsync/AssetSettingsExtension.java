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
package org.spongepowered.synchronizer.assetsync;

import akka.actor.AbstractExtensionId;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.actor.ExtensionIdProvider;
import com.typesafe.config.Config;
import io.vavr.collection.List;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

class AssetSettingsExtension extends AbstractExtensionId<AssetSettingsExtension.AssetRetrievalSettings>
    implements ExtensionIdProvider {
    public static final AssetSettingsExtension SettingsProvider = new AssetSettingsExtension();

    @Override
    public AssetRetrievalSettings createExtension(final ExtendedActorSystem system) {
        return new AssetRetrievalSettings(
            system.settings().config().getConfig("systemofadownload.synchronizer.worker.assets"));
    }

    @Override
    public AssetSettingsExtension lookup() {
        return SettingsProvider;
    }

    public static class AssetRetrievalSettings implements Extension {
        public final String repository;
        public final Duration timeout;
        public final int retryCount;
        public final List<String> filesToIndex;
        public final int poolSize;

        public AssetRetrievalSettings(Config config) {
            this.repository = config.getString("repository");
            this.retryCount = config.getInt("retry");
            final var seconds = config.getDuration("timeout", TimeUnit.SECONDS);
            this.timeout = Duration.ofSeconds(seconds);
            final var stringList = config.getStringList("files-to-index");
            this.filesToIndex = List.ofAll(stringList);
            this.poolSize = config.getInt("pool-size");
        }
    }
}
