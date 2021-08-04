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

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

@Immutable
@Entity(name = "VersionedAsset")
@Table(name = "artifact_versioned_assets",
    schema = "version")
public class JpaVersionedAsset implements Serializable {

    @Id
    @Column(name = "group_id")
    private String groupId;
    @Id
    @Column(name = "artifact_id")
    private String artifactId;

    @Id
    @Column(name = "version")
    private String version;

    @Id
    @Column(name = "classifier")
    private String classifier;

    @Id
    @Column(name = "extension")
    private String extension;

    @Column(name = "download_url")
    private String downloadUrl;

    @Lob
    @Type(type = "org.hibernate.type.BinaryType")
    @Column(name = "md5")
    private byte[] md5;

    @Lob
    @Type(type = "org.hibernate.type.BinaryType")
    @Column(name = "sha1")
    private byte[] sha1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "version",
            referencedColumnName = "version",
            nullable = false,
            updatable = false,
            insertable = false),
        @JoinColumn(name = "group_id",
            referencedColumnName = "group_id",
            nullable = false,
            updatable = false,
            insertable = false),
        @JoinColumn(name = "artifact_id",
            referencedColumnName = "artifact_id",
            nullable = false,
            updatable = false,
            insertable = false)
    })
    private JpaVersionedArtifactView versionView;

    public String getClassifier() {
        return classifier;
    }

    public String getExtension() {
        return extension;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public byte[] getMd5() {
        return md5;
    }

    public byte[] getSha1() {
        return sha1;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaVersionedAsset that = (JpaVersionedAsset) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(
            artifactId, that.artifactId) && Objects.equals(version, that.version) && Objects.equals(
            classifier, that.classifier) && Objects.equals(extension, that.extension) && Objects.equals(
            downloadUrl, that.downloadUrl) && Arrays.equals(md5, that.md5) && Arrays.equals(
            sha1, that.sha1) && Objects.equals(versionView, that.versionView);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(groupId, artifactId, version, classifier, extension, downloadUrl, versionView);
        result = 31 * result + Arrays.hashCode(md5);
        result = 31 * result + Arrays.hashCode(sha1);
        return result;
    }
}
