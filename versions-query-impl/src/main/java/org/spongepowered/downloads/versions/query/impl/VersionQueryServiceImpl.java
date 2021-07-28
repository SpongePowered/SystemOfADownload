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
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.query.api.VersionsQueryService;
import org.spongepowered.downloads.versions.query.api.models.QueryVersions;
import org.spongepowered.downloads.versions.query.api.models.TagCollection;
import org.spongepowered.downloads.versions.query.impl.models.JpaTaggedVersion;
import org.spongepowered.downloads.versions.query.impl.models.JpaVersionedArtifactView;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

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
            t -> selectArtifactVersions(t, groupId, artifactId, tags, limit, offset));
    }

    @Override
    public ServiceCall<NotUsed, QueryVersions.VersionDetails> versionDetails(
        final String groupId, final String artifactId, final String version
    ) {
        return null;
    }

    private static record ParameterizedTag(String tagName, String tagValue) {
    }

    private static QueryVersions.VersionInfo selectArtifactVersions(
        final EntityManager em, final String groupId,
        final String artifactId,
        final Optional<String> tags,
        final Optional<Integer> limitOpt,
        final Optional<Integer> offsetOpt
    ) {
        if (groupId.isBlank() || artifactId.isBlank()) {
            throw new NotFound("unknown artifact");
        }

        final int limit = limitOpt.map(l -> Math.min(Math.max(l, 1), 25)).orElse(25);
        final int offset = offsetOpt.map(o -> Math.max(o, 0)).orElse(0);

        final var sanitizedGroupId = groupId.toLowerCase(Locale.ROOT);
        final var sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT);
        try {
            final var discoveredTags = tags.map(rw -> rw.split(","))
                .map(List::of).orElseGet(List::of)
                .map(tag -> tag.split(":"))
                .filter(array -> array.length == 2)
                .map(array -> new ParameterizedTag(array[0].toLowerCase(Locale.ROOT), array[1].strip()));

            // basically, don't do any filters, just do a limit and offset on versioned artifact
            // if available
            if (discoveredTags.isEmpty()) {
                final int totalCount = em.createNamedQuery("VersionedArtifactView.count", Long.class)
                    .setParameter("groupId", sanitizedGroupId)
                    .setParameter("artifactId", sanitizedArtifactId)
                    .getSingleResult().intValue();
                final var untaggedVersions = em.createNamedQuery(
                    "VersionedArtifactView.findByArtifact", JpaVersionedArtifactView.class
                )
                    .setParameter("artifactId", sanitizedArtifactId)
                    .setParameter("groupId", sanitizedGroupId)
                    .getResultList();
                final var mappedByCoordinates = untaggedVersions.stream()
                    .map(JpaVersionedArtifactView::toMavenCoordinates)
                    .sorted(Comparator.comparing((MavenCoordinates coords) -> {
                        return new ComparableVersion(coords.version);
                    }).reversed())
                    .collect(List.collector())
                    .drop(offset)
                    .take(limit);
                final var versionsWithTags = mappedByCoordinates
                    .toSortedMap(
                        Comparator.comparing(ComparableVersion::new).reversed(), coords -> coords.version,
                        fetchTagCollectionByCoordinates(em)
                    );
                return new QueryVersions.VersionInfo(versionsWithTags, offset, limit, totalCount);
            }
            // Otherwise, get the tagged versions that match the given tags
            // which is a little advanced, because we'll have to literally gather the versioned values
            // that match the tags, then do a shake down
            final var map = discoveredTags.map(tag ->
                em.createNamedQuery("TaggedVersion.findAllMatchingTagValues", JpaTaggedVersion.class)
                    .setParameter("groupId", sanitizedGroupId)
                    .setParameter("artifactId", sanitizedArtifactId)
                    .setParameter("tagName", tag.tagName)
                    .setParameter("tagValue", tag.tagValue + "%")
                    .getResultStream()
                    .map(tv -> Tuple.of(tv.asMavenCoordinates(), Tuple.of(tv.getTagName(), tv.getTagValue())))
                    .collect(List.collector())
            ).flatMap(Value::toStream);
            var versionedTags = HashMap.<MavenCoordinates, Map<String, String>>empty();

            for (final Tuple2<MavenCoordinates, Tuple2<String, String>> tagged : map) {
                versionedTags = versionedTags.put(tagged.map(Function.identity(), HashMap::of), Map::merge);
            }
            final var wantedTagNames = discoveredTags.map(ParameterizedTag::tagName);
            final var allSortedVersions = versionedTags
                .filter((coordinates, tagMap) -> tagMap.keySet().containsAll(wantedTagNames))
                .keySet()
                .map(MavenCoordinates::asStandardCoordinates)
                .toSortedSet(Comparator.comparing(ComparableVersion::new).reversed());
            final var allFound = allSortedVersions
                .drop(offset)
                .take(limit)
                .map(MavenCoordinates::new)
                .toSortedMap(Function.identity(), fetchTagCollectionByCoordinates(em))
                .mapKeys(coords -> coords.version)
                .toSortedMap(Comparator.comparing(ComparableVersion::new).reversed(), Tuple2::_1, Tuple2::_2);
            return new QueryVersions.VersionInfo(allFound, offset, limit, allSortedVersions.size());

        } catch (PersistenceException e) {
            throw new TransportException(
                TransportErrorCode.InternalServerError, new ExceptionMessage("Internal Server Error", ""));
        }
    }

    private static Function<MavenCoordinates, TagCollection> fetchTagCollectionByCoordinates(
        EntityManager em
    ) {
        return coordinates ->
        {
            final var results = em.createNamedQuery(
                "TaggedVersion.findAllForVersion",
                JpaTaggedVersion.class
            ).setParameter("groupId", coordinates.groupId)
                .setParameter("artifactId", coordinates.artifactId)
                .setParameter("version", coordinates.version)
                .setMaxResults(10)
                .getResultList();
            final var tuple2Stream = results.stream().map(
                taggedVersion -> Tuple.of(taggedVersion.getTagName(), taggedVersion.getTagValue()));
            return new TagCollection(tuple2Stream
                .collect(HashMap.collector()), false);
        };
    }

}
