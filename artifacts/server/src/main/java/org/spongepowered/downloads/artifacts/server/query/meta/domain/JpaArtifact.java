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
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotEmpty;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;


@MappedEntity(value = "artifacts", schema = "artifact", alias = "artifact")
public record JpaArtifact(

    @GeneratedValue
    @Id
    int id,
    @MappedProperty(value = "group_id")
    String groupId,
    @NotEmpty
    @MappedProperty(value = "artifact_id")
    String artifactId,
    @MappedProperty(value = "display_name")
    String displayName,
    @MappedProperty(value = "website")
    String website,
    @MappedProperty(value = "git_repository")
    String gitRepo,
    @MappedProperty(value = "issues")
    String issues,

    @OneToMany(fetch = FetchType.EAGER, targetEntity = JpaArtifactTagValue.class)
    @JoinColumns({
        @JoinColumn(name = "artifact_id",
            referencedColumnName = "artifact_id"),
        @JoinColumn(name = "group_id",
            referencedColumnName = "group_id")
    })
    Set<JpaArtifactTagValue> tagValues
) {

    public JpaArtifact(String groupId, String name) {
        this(0, groupId, name, "", "", "", "", Set.of());
    }

    @Transient
    public ArtifactCoordinates coordinates() {
        return new ArtifactCoordinates(this.groupId, this.artifactId);
    }

    @Transient
    public Map<String, SortedSet<String>> tags() {
        return this.tagValues.stream()
            .collect(
                Collectors.groupingBy(
                    JpaArtifactTagValue::getTagName,
                    Collectors.mapping(
                        JpaArtifactTagValue::getTagValue,
                        Collectors.toCollection(TreeSet::new)
                    )
                )
            );
    }



}
