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
package org.spongepowered.downloads.versions.query.impl.models;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.hibernate.annotations.Immutable;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.query.api.models.TagCollection;
import org.spongepowered.downloads.versions.query.api.models.VersionedChangelog;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.net.URI;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Immutable
@Entity(name = "VersionedArtifactView")
@Table(name = "versioned_artifacts",
    schema = "version")
@NamedQueries({
    @NamedQuery(
        name = "VersionedArtifactView.count",
        query = """
                select count(v) from VersionedArtifactView v
                where v.groupId = :groupId and v.artifactId = :artifactId
                """
    ),
    @NamedQuery(
        name = "VersionedArtifactView.recommendedCount",
        query = """
                select count(v) from VersionedArtifactView v
                where v.groupId = :groupId and v.artifactId = :artifactId and v.recommended = :recommended
                """
    ),
    @NamedQuery(
        name = "VersionedArtifactView.findByArtifact",
        query = """
                select v from VersionedArtifactView v where v.artifactId = :artifactId and v.groupId = :groupId
                """
    ),
    @NamedQuery(
        name = "VersionedArtifactView.findByArtifactAndRecommendation",
        query = """
                select v from VersionedArtifactView v
                where v.artifactId = :artifactId and v.groupId = :groupId and (v.recommended = :recommended or v.manuallyRecommended = :recommended)
                """
    ),
    @NamedQuery(
        name = "VersionedArtifactView.findExplicitly",
        query = """
                select v from VersionedArtifactView v
                left join fetch v.tags
                where v.artifactId = :artifactId and v.groupId = :groupId and v.version = :version
                """
    ),
    @NamedQuery(
        name = "VersionedArtifactView.findFullVersionDetails",
        query = """
                select v from VersionedArtifactView v
                left join fetch v.tags
                left join fetch v.assets
                where v.artifactId = :artifactId and v.groupId = :groupId and v.version = :version
                """
    )
})
public class JpaVersionedArtifactView implements Serializable {

    @Id
    @Column(name = "artifact_id",
        updatable = false)
    private String artifactId;

    @Id
    @Column(name = "group_id",
        updatable = false)
    private String groupId;

    @Id
    @Column(name = "version",
        updatable = false)
    private String version;

    @Column(name = "recommended")
    private boolean recommended;

    @Column(name = "manual_recommendation")
    private boolean manuallyRecommended;

    @Column(name = "ordering")
    private int ordering;

    @OneToMany(
        targetEntity = JpaTaggedVersion.class,
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        mappedBy = "versionView")
    private Set<JpaTaggedVersion> tags;

    @OneToMany(
        targetEntity = JpaVersionedAsset.class,
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY,
        orphanRemoval = true,
        mappedBy = "versionView"
    )
    private Set<JpaVersionedAsset> assets;

    @OneToOne(
        targetEntity = JpaVersionedChangelog.class,
        mappedBy = "versionView",
        fetch = FetchType.LAZY
    )
    private JpaVersionedChangelog changelog;

    public Set<JpaTaggedVersion> getTags() {
        return tags;
    }

    public String version() {
        return this.version;
    }

    public Map<String, String> getTagValues() {
        final var results = this.getTags();
        final var tuple2Stream = results.stream().map(
            taggedVersion -> Tuple.of(taggedVersion.getTagName(), taggedVersion.getTagValue()));
        return tuple2Stream
            .collect(HashMap.collector());
    }

    public TagCollection asTagCollection() {
        final var results = this.getTags();
        final var tuple2Stream = results.stream().map(
            taggedVersion -> Tuple.of(taggedVersion.getTagName(), taggedVersion.getTagValue()));
        return new TagCollection(tuple2Stream
            .collect(HashMap.collector()), this.recommended);
    }

    public boolean isRecommended() {
        return this.recommended || this.manuallyRecommended;
    }

    public MavenCoordinates asMavenCoordinates() {
        return new MavenCoordinates(this.groupId + ":" + this.artifactId + ":" + this.version);
    }

    Set<JpaVersionedAsset> getAssets() {
        return assets;
    }

    public List<Artifact> asArtifactList() {
        return this.getAssets()
            .stream()
            .map(
                asset -> new Artifact(
                    Optional.ofNullable(asset.getClassifier()),
                    URI.create(asset.getDownloadUrl()),
                    new String(asset.getMd5()),
                    new String(asset.getSha1()),
                    asset.getExtension()
                )
            ).collect(List.collector())
            .sorted(Comparator.comparing(artifact -> artifact.classifier().orElse("")));
    }

    public Optional<VersionedChangelog> asVersionedCommit() {
        final var changelog = this.changelog;
        if (changelog == null) {
            return Optional.empty();
        }
        return Optional.of(changelog.getChangelog());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaVersionedArtifactView that = (JpaVersionedArtifactView) o;
        return Objects.equals(artifactId, that.artifactId) && Objects.equals(
            groupId, that.groupId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, groupId, version);
    }

}
