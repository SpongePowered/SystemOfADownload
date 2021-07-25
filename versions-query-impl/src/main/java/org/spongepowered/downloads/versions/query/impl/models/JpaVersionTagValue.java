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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Entity(name = "VersionTag")
@Table(name = "version_tags", schema = "version")
public class JpaVersionTagValue implements Serializable {

    @Serial private static final long serialVersionUID = -1999404429359178588L;

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id",
        foreignKey = @ForeignKey(name = "version_tags_version_id_fkey"))
    private JpaArtifactVersion version;

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id",
        foreignKey = @ForeignKey(name = "version_tags_tag_id_fkey"))
    private JpaArtifactTag tag;

    @Column(name = "tag_value")
    private String value;

    public JpaArtifactVersion getVersion() {
        return version;
    }

    public void setVersion(final JpaArtifactVersion version) {
        this.version = version;
    }

    public JpaArtifactTag getTag() {
        return tag;
    }

    public void setTag(final JpaArtifactTag tag) {
        this.tag = tag;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JpaVersionTagValue that = (JpaVersionTagValue) o;
        return Objects.equals(version, that.version)
            && Objects.equals(tag, that.tag)
            && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, tag, value);
    }
}
