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
package org.spongepowered.downloads.versions.worker.readside;

import akka.actor.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Routers;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import org.pcollections.PSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.downloads.versions.worker.actor.AssetRefresher;
import org.spongepowered.downloads.versions.worker.domain.GitEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.UUID;

@Singleton
public final record CommitProcessor(
    ReadSide readSide,
    JpaSession session
) {

    @Inject
    public CommitProcessor {
        readSide.register(CommitWriter.class);
    }

    static final class CommitWriter extends ReadSideProcessor<GitEvent> {

        private static final Logger LOGGER = LoggerFactory.getLogger("CommitWriter");

        private final JpaReadSide readSide;
        private final ActorRef<AssetRefresher.Command> refresher;

        @Inject
        CommitWriter(final JpaReadSide readSide, final JpaSession session, final ActorSystem system) {
            this.readSide = readSide;
            final var refresherUUID = UUID.randomUUID();
            final var assetRouter = Routers.group(AssetRefresher.serviceKey);
            this.refresher = Adapter.spawn(
                system,
                assetRouter,
                "versioned-artifact-repository-commit-refresher-" + refresherUUID);
        }

        @Override
        public ReadSideHandler<GitEvent> buildHandler() {
            return this.readSide.<GitEvent>builder("version-commit-writer")
                .setGlobalPrepare((em) -> {})
                .setEventHandler(
                    GitEvent.VersionRegistered.class,
                    (em, e) -> this.refresher.tell(new AssetRefresher.Refresh(e.coordinates().asArtifactCoordinates()))
                )
                .setEventHandler(
                    GitEvent.ArtifactRegistered.class,
                    (em, e) -> {}
                )
                .setEventHandler(
                    GitEvent.RepoRegistered.class,
                    (em, e) -> this.refresher.tell(new AssetRefresher.Refresh(e.coordinates()))
                )
                .setEventHandler(
                    GitEvent.CommitAssociatedWithVersion.class,
                    (em, e) -> {
                        final var results = em.createNamedQuery(
                                "GitVersionedArtifact.findByCoordinates", JpaVersionedArtifact.class)
                            .setParameter("groupId", e.coordinates().groupId)
                            .setParameter("artifactId", e.coordinates().artifactId)
                            .setParameter("version", e.coordinates().version)
                            .setMaxResults(1)
                            .getResultList();
                        results.forEach(version -> version.setCommit(e.sha()));
                    }
                )
                .build();
        }

        @Override
        public PSequence<AggregateEventTag<GitEvent>> aggregateTags() {
            return GitEvent.INSTANCE.allTags();
        }
    }

}
