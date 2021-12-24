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
package org.spongepowered.downloads.versions.server.readside;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import org.pcollections.PSequence;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.ArtifactEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class AssetReadsidePersistence {

    private final JpaSession session;

    @Inject
    public AssetReadsidePersistence(
        final ReadSide readSide,
        final JpaSession session
    ) {
        this.session = session;
        readSide.register(AssetReadsidePersistence.AssetWriter.class);
    }

    static final class AssetWriter extends ReadSideProcessor<ArtifactEvent> {

        private final JpaReadSide readSide;

        private static final AtomicInteger counter = new AtomicInteger();

        @Inject
        AssetWriter(final JpaReadSide readSide) {
            this.readSide = readSide;
        }

        @Override
        public ReadSideHandler<ArtifactEvent> buildHandler() {
            return this.readSide.<ArtifactEvent>builder("asset_read_side_processor_" + counter.incrementAndGet())
                .setGlobalPrepare((em) -> {})
                .setEventHandler(ArtifactEvent.AssetsUpdated.class, (em, event) -> {
                    final var coordinates = event.coordinates();
                    final var version = em.createNamedQuery(
                            "ArtifactVersion.findByCoordinates",
                            JpaArtifactVersion.class
                        )
                        .setParameter("groupId", coordinates.groupId)
                        .setParameter("artifactId", coordinates.artifactId)
                        .setParameter("version", coordinates.version)
                        .setMaxResults(1)
                        .getSingleResult();
                    event.artifacts()
                        .forEach(asset -> {
                            final var versionedAsset = findOrCreateVersionedAsset(em, version, asset);
                            versionedAsset.setDownloadUrl(asset.downloadUrl().toString());
                            versionedAsset.setMd5(asset.md5().getBytes(StandardCharsets.UTF_8));
                            versionedAsset.setSha1(asset.sha1().getBytes(StandardCharsets.UTF_8));
                            versionedAsset.setExtension(asset.extension());
                        });
                })
                .build();
        }

        private static JpaVersionedArtifactAsset findOrCreateVersionedAsset(
            EntityManager em, JpaArtifactVersion version, Artifact asset
        ) {
            return em.createNamedQuery(
                    "VersionedAsset.findByVersion",
                    JpaVersionedArtifactAsset.class
                )
                .setParameter("id", version.getId())
                .setParameter("classifier", asset.classifier().orElse(""))
                .setParameter("extension", asset.extension())
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseGet(() -> {
                    final var jpaAsset = new JpaVersionedArtifactAsset();
                    jpaAsset.setClassifier(asset.classifier().orElse(""));
                    version.addAsset(jpaAsset);
                    return jpaAsset;
                });
        }

        @Override
        public PSequence<AggregateEventTag<ArtifactEvent>> aggregateTags() {
            return ArtifactEvent.INSTANCE.allTags();
        }
    }
}
