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
package org.spongepowered.downloads.artifact.readside;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide;
import org.pcollections.PSequence;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.details.DetailsEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;

@Singleton
public class ArtifactReadside {

    @Inject
    public ArtifactReadside(final ReadSide readSide) {
        readSide.register(DetailsWriter.class);
    }

    static final class DetailsWriter extends ReadSideProcessor<DetailsEvent> {

        private final JpaReadSide readSide;

        @Inject
        DetailsWriter(final JpaReadSide readSide) {
            this.readSide = readSide;
        }

        @Override
        public ReadSideHandler<DetailsEvent> buildHandler() {
            return this.readSide.<DetailsEvent>builder("artifact-details-builder")
                .setEventHandler(DetailsEvent.ArtifactRegistered.class, (em, event) -> {
                    findOrRegisterArtifact(em, event.coordinates);
                })
                .setEventHandler(DetailsEvent.ArtifactDetailsUpdated.class, (em, event) -> {
                    final var artifact = findOrRegisterArtifact(em, event.coordinates);
                    artifact.setDisplayName(event.displayName);
                })
                .build();
        }

        private JpaArtifact findOrRegisterArtifact(
            final EntityManager em, final ArtifactCoordinates coordinates
        ) {
            final var artifactQuery = em.createNamedQuery(
                "Artifact.findById",
                JpaArtifact.class
            );
            return artifactQuery.setParameter("groupId", coordinates.groupId)
                .setParameter("artifactId", coordinates.artifactId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseGet(() -> {
                    final var jpaArtifact = new JpaArtifact();
                    jpaArtifact.setGroupId(coordinates.groupId);
                    jpaArtifact.setArtifactId(coordinates.artifactId);
                    em.persist(jpaArtifact);
                    return jpaArtifact;
                });
        }

        @Override
        public PSequence<AggregateEventTag<DetailsEvent>> aggregateTags() {
            return DetailsEvent.TAG.allTags();
        }
    }
}
