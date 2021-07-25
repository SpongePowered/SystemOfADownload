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
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.spongepowered.downloads.versions.query.api.VersionsQueryService;
import org.spongepowered.downloads.versions.query.api.models.QueryVersions;
import org.spongepowered.downloads.versions.query.api.models.TagCollection;
import org.spongepowered.downloads.versions.query.impl.models.JpaArtifact;
import org.spongepowered.downloads.versions.query.impl.models.JpaArtifactVersion;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

public class VersionQueryServiceImpl implements VersionsQueryService {

    private final JpaSession session;

    @Inject
    public VersionQueryServiceImpl(final JpaSession session) {
        this.session = session;
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

    private static record ParameterizedTagQuery(String tagNameKey, String tagNameValue, String tagValueKey,
                                                String tagValue) {
        String createQueryString() {
            return "t.name = :" + tagNameKey + " and vt.tag = t and vt.value = :" + tagValueKey;
        }

        public void injectToQuery(TypedQuery<JpaArtifact> query) {
            System.out.printf("TagName %s is %s with value %s being %s\n", this.tagNameKey, tagNameValue, tagValueKey, tagValue);
            query.setParameter(tagNameKey, tagNameValue);
            query.setParameter(tagValueKey, tagValue);
        }
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
        final int offset = offsetOpt.map(o -> Math.min(o, 0)).orElse(0);

        final var sanitizedGroupId = groupId.toLowerCase(Locale.ROOT);
        final var sanitizedArtifactId = artifactId.toLowerCase(Locale.ROOT);
        try {
            final var discoveredTags = tags.map(rw -> rw.split(",")).map(List::of).orElseGet(List::of)
                .map(tag -> tag.split(":"))
                .filter(array -> array.length == 2)
                .map(array -> new ParameterizedTag(array[0].toLowerCase(Locale.ROOT), array[1].strip()));
            final java.util.List<ParameterizedTagQuery> parameterJoining = new ArrayList<>();
            for (int i = 0; i < discoveredTags.size(); i++) {
                final var parameterizedTag = discoveredTags.get(i);
                parameterJoining.add(new ParameterizedTagQuery("tagName" + i, parameterizedTag.tagName, "tagValue" + i,
                    parameterizedTag.tagValue
                ));
            }
            final String completedQueryString;
            if (!parameterJoining.isEmpty()) {
                final var partialPQLString =
                """
                select a from Artifact a where exists (
                    select v from a.versions v
                    inner join a.tags t
                    inner join v.tagValues vt
                    where (a.groupId = :groupId and a.artifactId = :artifactId
                """;
                final var joiner = new StringJoiner(") and (", " and (", ")");
                parameterJoining.forEach(param -> joiner.add(param.createQueryString()));
                completedQueryString = partialPQLString + joiner + "))";
            } else {
                completedQueryString =
                """
                select a from Artifact a where exists (
                    select v from a.versions v
                    inner join a.tags t
                    inner join v.tagValues vt
                    where (a.groupId = :groupId and a.artifactId = :artifactId)
                )                             
                """;
            }
            System.out.println("Completed query:\n" + completedQueryString + "\n\n");
            final var query = em.createQuery(
                completedQueryString,
                JpaArtifact.class
            );
            query.setParameter("groupId", sanitizedGroupId);
            query.setParameter("artifactId", sanitizedArtifactId);
            if (!parameterJoining.isEmpty()) {
                parameterJoining.forEach(param -> param.injectToQuery(query));
            }
            if (query.getResultList().isEmpty()) {
                throw new NotFound("unknown artifact");
            }
            final var artifact = query.getSingleResult();

            return convertToVersions(artifact.getVersions(), limit, offset);

        } catch (PersistenceException e) {
            throw new TransportException(
                TransportErrorCode.InternalServerError, new ExceptionMessage("Internal Server Error", ""));
        }
    }

    private static QueryVersions.VersionInfo convertToVersions(
        final Set<JpaArtifactVersion> versions,
        final int limit,
        final int offset
    ) {
        final var size = versions.size();
        final var taggedVersions = versions.stream()
            .collect(HashMap.collector(JpaArtifactVersion::getVersion, v -> {
                final var tagValues = v.getTagValues().stream()
                    .map(tv -> Tuple.of(tv.getTag().getName(), tv.getValue()))
                    .collect(HashMap.collector());
                return new TagCollection(tagValues, false);
            }));
        final var trimmed = taggedVersions.toSortedMap(
            Comparator.comparing(ComparableVersion::new).reversed(), Tuple2::_1, Tuple2::_2)
            .toList()
            .drop(offset)
            .take(limit)
            .toSortedMap(Tuple2::_1, Tuple2::_2);
        return new QueryVersions.VersionInfo(trimmed, offset, limit, size);

    }

}
