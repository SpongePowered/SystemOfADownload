package org.spongepowered.downloads.artifacts.server.query.meta.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@MappedEntity
public class Group {

    @GeneratedValue
    @Id
    private Long id;

    @MappedProperty(value = "groupId")
    private String groupId;

    @Relation(value = Relation.Kind.ONE_TO_MANY,
        mappedBy = "groupId")
    private List<JpaArtifact> artifacts;

}
