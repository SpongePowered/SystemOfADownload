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

import com.google.common.collect.ImmutableMap;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import org.pcollections.PSequence;
import org.spongepowered.downloads.versions.collection.ACEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;

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

        @Inject
        VersionWriter(final JpaReadSide readSide) {
            this.readSide = readSide;
        }

        @Override
        public ReadSideHandler<ACEvent> buildHandler() {
            return this.readSide.<ACEvent>builder("version-query-builder")
                .setGlobalPrepare(this::createSchema)
                .setEventHandler(ACEvent.ArtifactCoordinatesUpdated.class, (em, artifactCreated) -> {
                    final var coordinates = artifactCreated.coordinates;
                    final var artifactQuery = em.createQuery(
                        """
                        select a from Artifact a where a.artifactId = :artifactId and a.groupId = :groupId
                        """, JpaArtifact.class);
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
                    final var artifactVersion = artifact.getVersions().stream().filter(
                        v -> v.getVersion().equals(version)).findFirst()
                        .orElseGet(() -> {
                            final var jpaArtifactVersion = new JpaArtifactVersion();
                            jpaArtifactVersion.setVersion(version);
                            artifact.addVersion(jpaArtifactVersion);
                            return jpaArtifactVersion;
                        });
                    final var rowsAffected = em.createNativeQuery(
                        """
                        insert into version_tags (version_id, tag_id, tag_value) (
                            select 
                            version.id                                                                                version_id,
                            artifact_tag.internal_id                                                                  tag_id,
                            ((regexp_match(version.version, artifact_tag.tag_regex))[artifact_tag.use_capture_group]) tag_value
                            from artifact_versions version
                            inner join artifacts a on a.id = version.artifact_id
                            inner join artifact_tags artifact_tag on a.group_id = artifact_tag.group_id and a.artifact_id = artifact_tag.artifact_id
                            where a.group_id = :groupId
                            and a.artifact_id = :artifactId
                            and version.version = :version
                        )
                        """
                    ).setParameter("groupId", coordinates.groupId)
                        .setParameter("artifactId", coordinates.artifactId)
                        .setParameter("version", coordinates.version)
                        .getFirstResult();
                    System.out.println("rows affected for version update: " + rowsAffected);

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
                    final var rowsAffected = em.createNativeQuery(
                        """
                        insert into version_tags (version_id, tag_id, tag_value) (
                            select version.id                                                                                version_id,
                                   artifact_tag.internal_id                                                                  tag_id,
                                   ((regexp_match(version.version, artifact_tag.tag_regex))[artifact_tag.use_capture_group]) tag_value
                            from artifact_versions version
                                     inner join artifacts a on version.artifact_id = a.id
                                     inner join artifact_tags artifact_tag
                                                on a.artifact_id = artifact_tag.artifact_id and artifact_tag.group_id = a.group_id
                            where a.group_id = :groupId
                              and a.artifact_id = :artifactId
                        )
                        """)
                        .setParameter("groupId", coordinates.groupId)
                        .setParameter("artifactId", coordinates.artifactId)
                        .getFirstResult();
                    System.out.println("rows affected for tag update: " + rowsAffected);
                })
                .setEventHandler(ACEvent.VersionTagged.class, (em, versionTagged) -> {

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
