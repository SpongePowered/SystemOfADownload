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
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.pcollections.PSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedChangelog;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;
import org.spongepowered.downloads.versions.worker.actor.AssetRefresher;
import org.spongepowered.downloads.versions.worker.domain.GitEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
            final var sharding = ClusterSharding.get(Adapter.toTyped(system));
            this.refresher = Adapter.spawn(
                system,
                assetRouter,
                "versioned-artifact-repository-commit-refresher-" + refresherUUID
            );
        }

        @Override
        public ReadSideHandler<GitEvent> buildHandler() {
            return this.readSide.<GitEvent>builder("version-commit-writer")
                .setGlobalPrepare((em) -> {
                })
                .setEventHandler(
                    GitEvent.VersionRegistered.class,
                    (em, e) -> this.refresher.tell(new AssetRefresher.Refresh(e.coordinates().asArtifactCoordinates()))
                )
                .setEventHandler(
                    GitEvent.ArtifactRegistered.class,
                    (em, e) -> {
                    }
                )
                .setEventHandler(
                    GitEvent.RepoRegistered.class,
                    (em, e) -> this.refresher.tell(new AssetRefresher.Refresh(e.coordinates()))
                )
                .setEventHandler(
                    GitEvent.ArtifactLabeledMissingCommit.class,
                    (em, e) -> {
                    }
                )
                .setEventHandler(
                    GitEvent.CommitAssociatedWithVersion.class,
                    (em, e) -> {
                        final var coordinates = e.coordinates();
                        final var results = getVersionedArtifacts(em, coordinates);
                        if (results.isEmpty()) {
                            return;
                        }
                        final JpaVersionedArtifact jpaVersionedArtifact = results.get(0);
                        if (jpaVersionedArtifact.getChangelog() == null) {
                            final var jpaVersionChangelog = new JpaVersionChangelog();
                            jpaVersionedArtifact.setChangelog(jpaVersionChangelog);
                        }
                        final var jpaChangelog = jpaVersionedArtifact.getChangelog();
                        final var author = new VersionedCommit.Author("", "");
                        final var committer = new VersionedCommit.Commiter("", "");
                        final ZonedDateTime epoch = ZonedDateTime.of(
                            LocalDate.EPOCH, LocalTime.MAX, ZoneId.systemDefault());
                        final URI repo = URI.create(jpaVersionedArtifact.getArtifact().getRepo());
                        final VersionedCommit rawCommit = new VersionedCommit(
                            "", "", e.sha(), author, committer, repo, epoch);
                        final var changelog = new VersionedChangelog(
                            List.of(new VersionedChangelog.IndexedCommit(rawCommit, List.empty())), true);
                        jpaChangelog.setSha(rawCommit.sha());
                        jpaChangelog.setBranch("foo");
                        jpaChangelog.setChangelog(changelog);
                        Try.of(repo::toURL)
                            .toJavaOptional()
                            .ifPresent(jpaChangelog::setRepo);
                        em.persist(jpaChangelog);
                    }
                )
                .setEventHandler(
                    GitEvent.CommitDetailsUpdated.class,
                    (em, e) -> {
                        final var coordinates = e.coordinates();
                        final var results = getVersionedArtifacts(em, coordinates);
                        if (results.isEmpty()) {
                            return;
                        }
                        final JpaVersionedArtifact jpaVersionedArtifact = results.get(0);
                        if (jpaVersionedArtifact.getChangelog() == null) {
                            final var jpaVersionChangelog = new JpaVersionChangelog();
                            jpaVersionedArtifact.setChangelog(jpaVersionChangelog);
                            jpaVersionChangelog.setSha(e.commit().sha());
                            jpaVersionChangelog.setBranch("foo");
                            Try.of(e.repo()::toURL)
                                .toJavaOptional()
                                .ifPresent(jpaVersionChangelog::setRepo);
                        }
                        final var jpaChangelog = jpaVersionedArtifact.getChangelog();
                        final var commit = new VersionedChangelog.IndexedCommit(e.commit(), List.empty());
                        final var changelog = new VersionedChangelog(List.of(commit), true);
                        jpaChangelog.setChangelog(changelog);
                        em.persist(jpaChangelog);
                    }
                )
                .build();
        }

        private java.util.List<JpaVersionedArtifact> getVersionedArtifacts(
            EntityManager em, MavenCoordinates coordinates
        ) {
            return em.createNamedQuery(
                    "GitVersionedArtifact.findByCoordinates", JpaVersionedArtifact.class)
                .setParameter("groupId", coordinates.groupId)
                .setParameter("artifactId", coordinates.artifactId)
                .setParameter("version", coordinates.version)
                .setMaxResults(1)
                .getResultList();
        }

        @Override
        public PSequence<AggregateEventTag<GitEvent>> aggregateTags() {
            return GitEvent.INSTANCE.allTags();
        }
    }

}
