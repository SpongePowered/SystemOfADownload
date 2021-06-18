package org.spongepowered.downloads.artifact.group.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.CompressedJsonable;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.utils.UUIDType5;

import java.util.UUID;

@JsonDeserialize
public class PopulatedState implements GroupState, CompressedJsonable {
    public final String groupCoordinates;
    public final String name;
    public final String website;
    public final Set<String> artifacts;
    public final UUID groupId;

    @JsonCreator
    public PopulatedState(
        final String groupCoordinates, final String name, final String website, final Set<String> artifacts
    ) {
        this.groupCoordinates = groupCoordinates;
        this.name = name;
        this.website = website;
        this.groupId = UUIDType5.nameUUIDFromNamespaceAndString(UUIDType5.NAMESPACE_OID, this.groupCoordinates);
        this.artifacts = artifacts;
    }

    public boolean isEmpty() {
        return this.groupCoordinates.isEmpty() || this.name.isEmpty();
    }

    public Group asGroup() {
        return new Group(this.groupCoordinates, this.name, this.website);
    }

    @Override
    public String website() {
        return this.website;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String groupCoordinates() {
        return this.groupCoordinates;
    }
}
