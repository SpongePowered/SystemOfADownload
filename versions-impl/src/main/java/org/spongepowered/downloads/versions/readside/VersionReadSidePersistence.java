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
package org.spongepowered.downloads.versions.readside;

import akka.actor.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import com.google.common.collect.ImmutableMap;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import org.pcollections.PSequence;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.versions.collection.ACEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class VersionReadSidePersistence {

    private final JpaSession session;

    @Inject
    public VersionReadSidePersistence(
        final ReadSide readSide,
        final JpaSession session
    ) {
        this.session = session;
        readSide.register(VersionWriter.class);
    }

    static final class VersionWriter extends ReadSideProcessor<ACEvent> {

        private final JpaReadSide readSide;
        private final ActorRef<VersionedTagWorker.Command> refresher;

        private static final AtomicInteger counter = new AtomicInteger();

        @Inject
        VersionWriter(final JpaReadSide readSide, final JpaSession session, final ActorSystem system) {
            this.readSide = readSide;
            final var taggedWorker = VersionedTagWorker.create(session);
            final var commandBehavior = Behaviors.supervise(taggedWorker).onFailure(SupervisorStrategy.restart());
            this.refresher = Adapter.spawn(
                system.classicSystem(), commandBehavior, "version-tag-db-worker-" + counter.incrementAndGet());
        }

        @Override
        public ReadSideHandler<ACEvent> buildHandler() {
            return this.readSide.<ACEvent>builder("version-query-builder")
                .setGlobalPrepare(this::createSchema)
                .setEventHandler(ACEvent.ArtifactCoordinatesUpdated.class, (em, artifactCreated) -> {
                    final var coordinates = artifactCreated.coordinates;
                    final var artifactQuery = em.createNamedQuery(
                        "Artifact.selectByGroupAndArtifact",
                        JpaArtifact.class
                    );
                    final var singleResult = artifactQuery.setParameter("groupId", coordinates.groupId)
                        .setParameter("artifactId", coordinates.artifactId)
                        .setMaxResults(1)
                        .getResultList();
                    if (singleResult.isEmpty()) {
                        final var jpaArtifact = new JpaArtifact();
                        jpaArtifact.setGroupId(coordinates.groupId);
                        jpaArtifact.setArtifactId(coordinates.artifactId);
                        em.persist(jpaArtifact);
                    }
                })
                .setEventHandler(ACEvent.ArtifactVersionRegistered.class, (em, versionRegistered) -> {
                    final var coordinates = versionRegistered.version;
                    final var query = em.createNamedQuery(
                        "Artifact.selectByGroupAndArtifact",
                        JpaArtifact.class
                    );
                    query.setParameter("groupId", coordinates.groupId);
                    query.setParameter("artifactId", coordinates.artifactId);
                    final var artifact = query.getSingleResult();
                    final var version = coordinates.version;
                    em.createNamedQuery(
                            "ArtifactVersion.findByVersion",
                            JpaArtifactVersion.class
                        )
                        .setParameter("artifactId", artifact.getId())
                        .setParameter("version", version)
                        .setMaxResults(1)
                        .getResultList()
                        .stream().findFirst()
                        .orElseGet(() -> {
                            final var jpaArtifactVersion = new JpaArtifactVersion();
                            jpaArtifactVersion.setVersion(version);
                            artifact.addVersion(jpaArtifactVersion);
                            refresher.tell(new VersionedTagWorker.RefreshVersionTags());
                            return jpaArtifactVersion;
                        });
                })
                .setEventHandler(ACEvent.ArtifactTagRegistered.class, (em, tagRegistered) -> {
                    final var coordinates = tagRegistered.coordinates();
                    final var artifactQuery = em.createNamedQuery(
                        "Artifact.selectWithTags",
                        JpaArtifact.class
                    );
                    artifactQuery.setParameter("groupId", coordinates.groupId);
                    artifactQuery.setParameter("artifactId", coordinates.artifactId);
                    final var tag = tagRegistered.entry();
                    final var artifact = artifactQuery.getSingleResult();
                    final var jpaTag = artifact.getTags().stream()
                        .filter(jpatag -> jpatag.getName().equals(tag.name()))
                        .findFirst()
                        .orElseGet(() -> {
                            final var jpaArtifactTag = new JpaArtifactTag();
                            artifact.addTag(jpaArtifactTag);
                            return jpaArtifactTag;
                        });
                    jpaTag.setRegex(tag.regex());
                    jpaTag.setName(tag.name());
                    jpaTag.setGroup(tag.matchingGroup());
                    refresher.tell(new VersionedTagWorker.RefreshVersionTags());
                })
                .setEventHandler(ACEvent.VersionTagged.class, (em, versionTagged) -> {

                })
                .setEventHandler(ACEvent.PromotionSettingModified.class, (em, promotion) -> {
                    final var coordinates = promotion.coordinates();
                    final var artifactQuery = em.createNamedQuery(
                        "Artifact.selectWithTags",
                        JpaArtifact.class
                    );

                    artifactQuery.setParameter("groupId", coordinates.groupId);
                    artifactQuery.setParameter("artifactId", coordinates.artifactId);
                    final var artifact = artifactQuery.getSingleResult();
                    final var recommendation = em.createNamedQuery(
                            "RegexRecommendation.findByArtifact", JpaArtifactRegexRecommendation.class)
                        .setParameter("artifactId", artifact.getId())
                        .getResultList()
                        .stream()
                        .findFirst()
                        .orElseGet(() -> {
                            final var regexRecommendation = new JpaArtifactRegexRecommendation();
                            artifact.setRecommendation(regexRecommendation);
                            return regexRecommendation;
                        });
                    recommendation.setRegex(promotion.regex());
                    recommendation.setManual(promotion.enableManualPromotion());

                    refresher.tell(new VersionedTagWorker.RefreshVersionRecommendation(promotion.coordinates()));
                })
                .build();
        }

        private void createSchema(EntityManager em) {
            Persistence.generateSchema("default", ImmutableMap.of("hibernate.hbm2ddl.auto", "update"));
        }

        @Override
        public PSequence<AggregateEventTag<ACEvent>> aggregateTags() {
            return ACEvent.INSTANCE.allTags();
        }
    }

}
