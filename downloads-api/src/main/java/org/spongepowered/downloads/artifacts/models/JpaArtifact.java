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
package org.spongepowered.downloads.artifacts.models;


import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import io.vavr.collection.SortedSet;
import io.vavr.collection.TreeMap;
import io.vavr.collection.TreeSet;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.hibernate.annotations.Immutable;
import org.spongepowered.downloads.api.ArtifactCoordinates;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Immutable
@Entity(name = "Artifact")
@Table(name = "artifacts",
    schema = "version")
@NamedQueries({
    @NamedQuery(
        name = "Artifact.findByCoordinates",
        query = "select a from Artifact a where a.groupId = :groupId and a.artifactId = :artifactId"
    )
})
public class JpaArtifact implements Serializable {

    @Id
    @Column(name = "id",
        nullable = false,
        updatable = false,
        insertable = false)
    private int id;

    @Column(name = "group_id",
        nullable = false,
        updatable = false,
        insertable = false)
    private String groupId;

    @Column(name = "artifact_id",
        nullable = false,
        updatable = false,
        insertable = false)
    private String artifactId;

    @Column(name = "display_name",
        updatable = false,
        insertable = false)
    private String displayName;

    @Column(name = "website",
        updatable = false,
        insertable = false)
    private String website;

    @Column(name = "git_repository",
        updatable = false,
        insertable = false)
    private String gitRepo;

    @Column(name = "issues",
        updatable = false,
        insertable = false)
    private String issues;

    @OneToMany(fetch = FetchType.EAGER,
        targetEntity = JpaArtifactTagValue.class)
    @JoinColumns({
        @JoinColumn(name = "artifact_id",
            referencedColumnName = "artifact_id"),
        @JoinColumn(name = "group_id",
            referencedColumnName = "group_id")
    })
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaArtifact that = (JpaArtifact) o;
        return id == that.id && groupId.equals(that.groupId) && artifactId.equals(
            that.artifactId) && Objects.equals(displayName, that.displayName) && Objects.equals(
            website, that.website) && Objects.equals(gitRepo, that.gitRepo) && Objects.equals(
            issues, that.issues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, groupId, artifactId, displayName, website, gitRepo, issues);
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
