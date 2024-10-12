package org.spongepowered.downloads.artifacts.server.cmd.group.domain;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

import java.util.List;
import java.util.Objects;

@MappedEntity(value = "artifacts", schema = "artifact")
public class Artifact {
    @Id
    @GeneratedValue(GeneratedValue.Type.AUTO)
    @AutoPopulated
    private Long id;

    @Column(nullable = false)
    private String artifactId;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    @Column(nullable = false, name = "group_id")
    private Group group;

    @Column(nullable = false, name = "displayName")
    private String name;

    private String description;
    @Column(columnDefinition = "jsonb")
    private List<String> gitRepo;
    private String website;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public ArtifactCoordinates coordinates() {
        return new ArtifactCoordinates(this.group.groupId(), this.artifactId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Artifact artifact = (Artifact) o;
        return Objects.equals(artifactId, artifact.artifactId) && Objects.equals(group, artifact.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, group);
    }
}
