package org.spongepowered.downloads.artifacts.server.cmd.group.domain;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import jakarta.persistence.Column;

import java.util.List;
import java.util.Objects;

@MappedEntity(value = "groups", schema = "artifact")
public final class Group {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @AutoPopulated
    private Long id;
    @Column(unique = true, nullable = false, name = "group_id")
    private String groupId;
    @Column(nullable = false)
    private String name;

    private String website;
    @Relation(value = Relation.Kind.ONE_TO_MANY,
        mappedBy = "group")
    private List<Artifact> artifacts;

    public Long id() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setGroupId(final String groupId) {
        this.groupId = groupId;
    }

    public String groupId() {
        return groupId;
    }

    public String name() {
        return name;
    }

    public String website() {
        return website;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setWebsite(final String website) {
        this.website = website;
    }

    public void setArtifacts(final List<Artifact> artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return Objects.equals(groupId, group.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId);
    }
}
