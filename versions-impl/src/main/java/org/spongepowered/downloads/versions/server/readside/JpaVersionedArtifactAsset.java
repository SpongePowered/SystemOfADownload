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
package org.spongepowered.downloads.versions.server.readside;

import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.Objects;

@Entity(name = "VersionedAsset")
@Table(name = "versioned_assets",
    schema = "version")
@NamedQueries({
    @NamedQuery(
        name = "VersionedAsset.findByVersion",
        query =
            """
            select a from VersionedAsset a
            where a.versionedArtifact.id = :id and a.classifier = :classifier
            and a.extension = :extension
            """
    )
})
public class JpaVersionedArtifactAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id",
        updatable = false,
        nullable = false)
    private long id;

    @ManyToOne(targetEntity = JpaArtifactVersion.class,
        fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id",
        referencedColumnName = "id",
        nullable = false)
    private JpaArtifactVersion versionedArtifact;

    @Column(name = "classifier",
        nullable = false)
    private String classifier;

    @Column(name = "download_url",
        nullable = false)
    private String downloadUrl;

    @Column(name = "extension",
        nullable = false)
    private String extension;

    @Lob
    @Type(type = "org.hibernate.type.BinaryType")
    @Column(name = "md5")
    private byte[] md5;

    @Lob
    @Type(type = "org.hibernate.type.BinaryType")
    @Column(name = "sha1")
    private byte[] sha1;

    public void setClassifier(final String classifier) {
        this.classifier = classifier;
    }

    public void setExtension(final String extension) {
        this.extension = extension;
    }

    public void setDownloadUrl(final String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public void setMd5(final byte[] md5) {
        this.md5 = md5;
    }

    public void setSha1(final byte[] sha1) {
        this.sha1 = sha1;
    }

    void setVersion(JpaArtifactVersion jpaArtifactVersion) {
        this.versionedArtifact = jpaArtifactVersion;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaVersionedArtifactAsset that = (JpaVersionedArtifactAsset) o;
        return id == that.id && Objects.equals(
            versionedArtifact, that.versionedArtifact) && Objects.equals(
            classifier, that.classifier) && Objects.equals(
            downloadUrl, that.downloadUrl) && Objects.equals(
            extension, that.extension) && Arrays.equals(
            md5, that.md5) && Arrays.equals(sha1, that.sha1);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, versionedArtifact, classifier, downloadUrl, extension);
        result = 31 * result + Arrays.hashCode(md5);
        result = 31 * result + Arrays.hashCode(sha1);
        return result;
    }
}
