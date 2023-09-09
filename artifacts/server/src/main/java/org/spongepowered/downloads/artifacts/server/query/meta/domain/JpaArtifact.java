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
package org.spongepowered.downloads.artifacts.server.query.meta.domain;


import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import io.vavr.collection.SortedSet;
import io.vavr.collection.TreeMap;
import io.vavr.collection.TreeSet;
import jakarta.validation.constraints.NotEmpty;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

import java.util.Comparator;
import java.util.Set;


@MappedEntity(value = "artifacts", schema = "artifacts", alias = "artifact")
public class JpaArtifact {

    @GeneratedValue
    @Id
    private int id;

    public int getId() {
        return id;
    }

    @MappedProperty(value = "group_id")
    private String groupId;

    @NotEmpty
    @MappedProperty(value = "artifact_id")
    private String artifactId;

    @MappedProperty(value = "display_name")
    private String displayName;

    @MappedProperty(value = "website")
    private String website;

    @MappedProperty(value = "git_repository")
    private String gitRepo;

    @MappedProperty(value = "issues")
    private String issues;

    private Set<JpaArtifactTagValue> tagValues;

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getWebsite() {
        return website;
    }

    public String getGitRepo() {
        return gitRepo;
    }

    public String getIssues() {
        return issues;
    }

    public Set<JpaArtifactTagValue> getTagValues() {
        return tagValues;
    }

    public ArtifactCoordinates getCoordinates() {
        return new ArtifactCoordinates(this.groupId, this.artifactId);
    }

    public Map<String, SortedSet<String>> getTagValuesForReply() {
        final var tagValues = this.getTagValues();
        final var tagTuples = tagValues.stream()
            .map(value -> Tuple.of(value.getTagName(), value.getTagValue()))
            .toList();

        var versionedTags = TreeMap.<String, SortedSet<String>>empty();
        final var comparator = Comparator.comparing(ComparableVersion::new).reversed();

        for (final Tuple2<String, String> tagged : tagTuples) {
            versionedTags = versionedTags.put(tagged._1, TreeSet.of(comparator, tagged._2), SortedSet::addAll);
        }
        return versionedTags;
    }

}
