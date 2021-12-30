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
import org.spongepowered.downloads.versions.worker.domain.versionedartifact.ArtifactEvent;
import org.spongepowered.downloads.versions.worker.readside.model.JpaVersionChangelog;
import org.spongepowered.downloads.versions.worker.readside.model.JpaVersionedArtifact;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

@Singleton
public final record CommitProcessor(
    ReadSide readSide,
    JpaSession session
) {

    @Inject
    public CommitProcessor {
        readSide.register(CommitWriter.class);
    }

    static final class CommitWriter extends ReadSideProcessor<ArtifactEvent> {

        private static final Logger LOGGER = LoggerFactory.getLogger("CommitWriter");
        public static final ZonedDateTime EPOCH = ZonedDateTime.of(LocalDateTime.MIN, ZoneOffset.UTC);

        private final JpaReadSide readSide;

        @Inject
        CommitWriter(final JpaReadSide readSide, final JpaSession session, final ActorSystem system) {
            this.readSide = readSide;
        }

        @Override
        public ReadSideHandler<ArtifactEvent> buildHandler() {
            return this.readSide.<ArtifactEvent>builder("version-commit-writer")
                .setGlobalPrepare((em) -> {
                })
                .setEventHandler(ArtifactEvent.FilesErrored.class, (em, e) -> {
                })
                .setEventHandler(ArtifactEvent.Registered.class, (em, e) -> {
                })
                .setEventHandler(ArtifactEvent.AssetsUpdated.class, (em, e) -> {
                })
                .setEventHandler(
                    ArtifactEvent.CommitAssociated.class,
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
                        final var artifactRepo = jpaVersionedArtifact.getArtifact().getRepo();
                        final var commitLink = Optional.ofNullable(artifactRepo).map(URI::create);
                        final VersionedCommit rawCommit = new VersionedCommit(
                            "", "", e.commitSha(), author, committer, commitLink, epoch);
                        final var changelog = new VersionedChangelog(
                            List.of(new VersionedChangelog.IndexedCommit(rawCommit, List.empty())), true);
                        jpaChangelog.setSha(rawCommit.sha());
                        jpaChangelog.setChangelog(changelog);
                        Try.ofSupplier(commitLink::get)
                            .mapTry(URI::toURL)
                            .toJavaOptional()
                            .ifPresent(jpaChangelog::setRepo);
                        em.persist(jpaChangelog);
                    }
                )
                .setEventHandler(
                    ArtifactEvent.CommitResolved.class,
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
                            jpaVersionChangelog.setSha(e.versionedCommit().sha());
                            jpaVersionChangelog.setBranch("foo");
                            final var commitUrl = e.repo().toString().replace(".git", "") + "/commit/" + e.versionedCommit().sha();
                            Try.of(() -> new URL(commitUrl))
                                .toJavaOptional()
                                .ifPresent(jpaVersionChangelog::setRepo);
                        }
                        final var jpaChangelog = jpaVersionedArtifact.getChangelog();
                        final var commit = new VersionedChangelog.IndexedCommit(e.versionedCommit(), List.empty());
                        final var changelog = new VersionedChangelog(List.of(commit), true);
                        jpaChangelog.setChangelog(changelog);
                        em.persist(jpaChangelog);
                    }
                )
                .setEventHandler(
                    ArtifactEvent.CommitUnresolved.class,
                    (em, e) -> {
                        final var coordinates = e.coordinates();
                        final var results = getVersionedArtifacts(em, coordinates);
                        if (results.isEmpty()) {
                            return;
                        }
                        final JpaVersionedArtifact jpaVersionedArtifact = results.get(0);
                        final var changelog = new VersionedChangelog(List.of(
                            new VersionedChangelog.IndexedCommit(
                                convertToInvalidCommit(e), List.empty()
                            )), false);
                        if (jpaVersionedArtifact.getChangelog() == null) {
                            final var jpaVersionChangelog = new JpaVersionChangelog();
                            jpaVersionedArtifact.setChangelog(jpaVersionChangelog);
                            jpaVersionChangelog.setSha(e.commitId());
                            jpaVersionChangelog.setBranch("foo");
                            jpaVersionChangelog.setChangelog(changelog);
                        }
                        final var jpaChangelog = jpaVersionedArtifact.getChangelog();
                        jpaChangelog.setChangelog(changelog);
                        em.persist(jpaChangelog);
                    }
                )
                .build();
        }

        private static VersionedCommit convertToInvalidCommit(ArtifactEvent.CommitUnresolved e) {
            return new VersionedCommit(
                "Commit not available",
                """
                This build has a commit that cannot be resolved, it may be
                possible to resolve it in some backup, but generally this
                build will not be supported by the community due to the
                lack of a commit.
                """,
                e.commitId(),
                new VersionedCommit.Author("", ""),
                new VersionedCommit.Commiter("", ""),
                Optional.empty(),
                EPOCH
            );
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
        public PSequence<AggregateEventTag<ArtifactEvent>> aggregateTags() {
            return ArtifactEvent.INSTANCE.allTags();
        }
    }

}
