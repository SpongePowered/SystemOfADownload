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

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity(name = "ArtifactTag")
@Table(name = "artifact_tags",
    schema = "version")
public class JpaArtifactTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "internal_id")
    private int id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "group_id", referencedColumnName = "group_id", nullable = false),
        @JoinColumn(name = "artifact_id", referencedColumnName = "artifact_id", nullable = false)
    })
    private JpaArtifact artifact;

    @Column(name = "tag_name",
        nullable = false)
    private String name;

    @Column(name = "tag_regex")
    private String regex;

    @Column(name = "use_capture_group")
    private int group;

    @OneToMany(
        mappedBy = "tag",
        orphanRemoval = true,
        cascade = CascadeType.ALL,
        targetEntity = JpaVersionTagValue.class
    )
    private Set<JpaVersionTagValue> versionTags  = new HashSet<>();

    public JpaArtifactTag() {
    }

    public JpaArtifact getArtifact() {
        return artifact;
    }

    void setArtifact(final JpaArtifact taggedVersion) {
        this.artifact = taggedVersion;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(final String regex) {
        this.regex = regex;
    }

    public int getGroup() {
        return group;
    }

    public void setGroup(final int group) {
        this.group = group;
    }

    public void addVersion(JpaVersionTagValue versionTagValue) {
        this.versionTags.add(versionTagValue);
        versionTagValue.setTag(this);
    }
}
