package org.spongepowered.downloads.artifacts.server.query.group.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;
import org.spongepowered.downloads.artifacts.server.query.meta.domain.JpaArtifact;

import java.util.List;

@MappedEntity(value = "groups")
@Serdeable
public class GroupOrg {

    @Id
    @GeneratedValue
    private int id;

    @MappedProperty(value = "groupId")
    private String groupId;

    @Relation(value =Relation.Kind.ONE_TO_MANY, mappedBy = "groupId")
    private List<JpaArtifact> artifacts;

}
