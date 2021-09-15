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
package org.spongepowered.downloads.versions.query.impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.api.transport.TransportException;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Value;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.versions.query.api.VersionsQueryService;
import org.spongepowered.downloads.versions.query.api.models.QueryVersions;
import org.spongepowered.downloads.versions.query.impl.models.JpaTaggedVersion;
import org.spongepowered.downloads.versions.query.impl.models.JpaVersionedArtifactView;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

public record VersionQueryServiceImpl(JpaSession session)
    implements VersionsQueryService {

    @Inject
    public VersionQueryServiceImpl {
    }

    @Override
    public ServiceCall<NotUsed, QueryVersions.VersionInfo> artifactVersions(
        final String groupId,
        final String artifactId,
        final Optional<String> tags,
        final Optional<Integer> limit,
        final Optional<Integer> offset,
        final Optional<Boolean> recommended
    ) {
        return request -> this.session.withTransaction(
            t -> {
                if (groupId.isBlank() || artifactId.isBlank()) {
                    throw new NotFound("unknown artifact");
                }
                try {
                    final var query = new VersionQuery(groupId, artifactId, tags, limit, offset, recommended);

                    if (query.tags.isEmpty()) {
                        return getUntaggedVersions(t, query);
                    }
                    return getTaggedVersions(t, query);
                } catch (PersistenceException e) {
                    throw new TransportException(
                        TransportErrorCode.InternalServerError, new ExceptionMessage("Internal Server Error", ""));
                }
            });
    }

    @Override
    public ServiceCall<NotUsed, QueryVersions.VersionDetails> latestArtifact(
        final String groupId,
        final String artifactId,
        final Optional<String> tags,
        final Optional<Boolean> recommended
    ) {
        return request -> this.session.withTransaction(
            t -> {
                if (groupId.isBlank() || artifactId.isBlank()) {
                    throw new NotFound("unknown artifact");
                }
                try {
                    final var query = new VersionQuery(groupId, artifactId, tags, recommended.orElse(false));

                    final var info = query.tags.isEmpty() ? getUntaggedVersions(t, query) : getTaggedVersions(t, query);
                    final var version = info.artifacts().keySet().head();
                    final var coordinates = query.coordinates.version(version);
                    final var versionedArtifact = t.createNamedQuery(
                            "VersionedArtifactView.findExplicitly", JpaVersionedArtifactView.class)
                        .setParameter("groupId", coordinates.groupId)
                        .setParameter("artifactId", coordinates.artifactId)
                        .setParameter("version", coordinates.version)
                        .getSingleResult();
                    return new QueryVersions.VersionDetails(
                        coordinates, versionedArtifact.asArtifactList(),
                        versionedArtifact.getTagValues(), versionedArtifact.isRecommended()
                    );
                } catch (PersistenceException e) {
                    throw new TransportException(
                        TransportErrorCode.InternalServerError, new ExceptionMessage("Internal Server Error", ""));
                }
            });
    }

    @Override
    public ServiceCall<NotUsed, QueryVersions.VersionDetails> versionDetails(
        final String groupId, final String artifactId, final String version
    ) {
        return notUsed -> this.session.withTransaction(em -> {
            final var sanitizedGroupId = groupId.toLowerCase(Locale.ROOT).trim();
            final var sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT).trim();
            final var sanitizedVersion = version.trim();
            return em.createNamedQuery("VersionedArtifactView.findFullVersionDetails", JpaVersionedArtifactView.class)
                .setParameter("groupId", sanitizedGroupId)
                .setParameter("artifactId", sanitizedArtifactId)
                .setParameter("version", sanitizedVersion)
                .getResultList()
                .stream()
                .findFirst()
                .map(versionView -> {
                    final var coordinates = versionView.asMavenCoordinates();
                    final var assets = versionView.asArtifactList();
                    final var tags = versionView.getTagValues();
                    return new QueryVersions.VersionDetails(coordinates, assets, tags, versionView.isRecommended());
                })
                .orElseThrow(() -> new NotFound("group or artifact or version not found"));
        });
    }

    private static record ParameterizedTag(String tagName, String tagValue) {
    }

    private static record VersionQuery(
        ArtifactCoordinates coordinates,
        int limit,
        int offset,
        Optional<Boolean> recommended,
        List<ParameterizedTag> tags) {

        VersionQuery(
            String groupId, String artifactId,
            Optional<String> tags,
            boolean recommended
        ) {
            this(
                new ArtifactCoordinates(groupId.toLowerCase(Locale.ROOT), artifactId.toLowerCase(Locale.ROOT)),
                25,
                0,
                Optional.of(recommended),
                gatherTags(tags)
            );
        }

        VersionQuery(
            String groupId, String artifactId,
            final Optional<String> tags,
            final Optional<Integer> limitOpt,
            final Optional<Integer> offsetOpt,
            final Optional<Boolean> recommended
        ) {
            this(
                new ArtifactCoordinates(groupId.toLowerCase(Locale.ROOT), artifactId.toLowerCase(Locale.ROOT)),
                limitOpt.map(l -> Math.min(Math.max(l, 1), 25)).orElse(25),
                offsetOpt.map(o -> Math.max(o, 0)).orElse(0),
                recommended,
                gatherTags(tags)
            );
        }

        private static List<ParameterizedTag> gatherTags(Optional<String> tags) {
            return tags.map(rw -> rw.split(","))
                .map(List::of).orElseGet(List::of)
                .map(tag -> tag.split(":"))
                .filter(array -> array.length == 2)
                .map(array -> new ParameterizedTag(array[0].toLowerCase(Locale.ROOT), array[1].strip()));
        }
    }

    private static QueryVersions.VersionInfo getUntaggedVersions(
        EntityManager em, VersionQuery query
    ) {
        final int totalCount = query.recommended
            .map(isRecommended -> em.createNamedQuery("VersionedArtifactView.recommendedCount", Long.class)
                .setParameter("recommended", isRecommended)
            )
            .orElseGet(() -> em.createNamedQuery("VersionedArtifactView.count", Long.class))
            .setParameter("groupId", query.coordinates.groupId)
            .setParameter("artifactId", query.coordinates.artifactId)
            .getSingleResult().intValue();
        if (totalCount <= 0) {
            throw new NotFound("group or artifact not found");
        }
        final var untaggedVersions = query.recommended
            .map(isRecommended -> em.createNamedQuery(
                        "VersionedArtifactView.findByArtifactAndRecommendation",
                        JpaVersionedArtifactView.class
                    )
                    .setParameter("recommended", isRecommended)
            )
            .orElseGet(() -> em.createNamedQuery(
                "VersionedArtifactView.findByArtifact", JpaVersionedArtifactView.class
            ))
            .setParameter("groupId", query.coordinates.groupId)
            .setParameter("artifactId", query.coordinates.artifactId)
            .getResultList();
        final var mappedByCoordinates = untaggedVersions.stream()
            .sorted(Comparator.comparing((JpaVersionedArtifactView coords) -> {
                return new ComparableVersion(coords.version());
            }).reversed())
            .collect(List.collector())
            .drop(query.offset)
            .take(query.limit);
        final var versionsWithTags = mappedByCoordinates
            .toSortedMap(
                Comparator.comparing(ComparableVersion::new).reversed(),
                JpaVersionedArtifactView::version,
                JpaVersionedArtifactView::asTagCollection
            );
        return new QueryVersions.VersionInfo(versionsWithTags, query.offset, query.limit, totalCount);
    }

    private static QueryVersions.VersionInfo getTaggedVersions(
        EntityManager em, VersionQuery query
    ) {
        // Otherwise, get the tagged versions that match the given tags
        // which is a little advanced, because we'll have to literally gather the versioned values
        // that match the tags, then do a shake down
        final var map = query.tags.map(tag -> query.recommended.map(
                recommended -> em.createNamedQuery(
                        "TaggedVersion.findMatchingTagValuesAndRecommendation",
                        JpaTaggedVersion.class
                    )
                    .setParameter("recommended", recommended))
            .orElseGet(() -> em.createNamedQuery(
                "TaggedVersion.findAllMatchingTagValues", JpaTaggedVersion.class
            ))
            .setParameter("groupId", query.coordinates.groupId)
            .setParameter("artifactId", query.coordinates.artifactId)
            .setParameter("tagName", tag.tagName)
            .setParameter("tagValue", tag.tagValue + "%")
            .getResultStream()
            .map(tv -> Tuple.of(tv, Tuple.of(tv.getTagName(), tv.getTagValue())))
            .collect(List.collector())
        ).flatMap(Value::toStream);
        if (map.isEmpty()) {
            throw new NotFound("group or artifact not found");
        }
        var versionedTags = HashMap.<String, Tuple2<JpaVersionedArtifactView, Map<String, String>>>empty();

        for (final Tuple2<JpaTaggedVersion, Tuple2<String, String>> tagged : map) {
            versionedTags = versionedTags.put(
                tagged._1.getMavenVersion(),
                tagged.map(JpaTaggedVersion::getVersion, HashMap::of),
                (o, i) -> Tuple.of(o._1, o._2.merge(i._2))
            );
        }
        final var wantedTagNames = query.tags.map(ParameterizedTag::tagName);
        final var validatedVersion = versionedTags
            .filter((coordinates, tagMap) -> tagMap._2.keySet().containsAll(wantedTagNames));
        final var versionsForQuery = validatedVersion
            .toSortedMap(Comparator.comparing(ComparableVersion::new).reversed(), tuple -> tuple._1, tuple -> tuple._2)
            .drop(query.offset)
            .take(query.limit);
        return new QueryVersions.VersionInfo(
            versionsForQuery.mapValues(tuple -> tuple._1.asTagCollection()), query.offset, query.limit,
            validatedVersion.size()
        );
    }

}
