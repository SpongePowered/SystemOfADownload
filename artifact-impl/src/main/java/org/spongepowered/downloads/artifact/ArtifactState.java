package org.spongepowered.downloads.artifact;

import io.vavr.collection.HashMultimap;
import io.vavr.collection.Map;
import io.vavr.collection.Multimap;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.Group;

import java.util.UUID;
import java.util.function.Function;

public class ArtifactState {

    private final Map<String, Group> groupByName;
    private final Map<UUID, Group> groupById;
    private final Multimap<Group, Artifact> groupArtifacts;

    public ArtifactState(final Multimap<Group, Artifact> groupArtifacts) {
        this.groupArtifacts = groupArtifacts;
        this.groupByName = groupArtifacts.keySet().toSortedMap(Group::getName, Function.identity());
        this.groupById = groupArtifacts.keySet().toSortedMap(Group::getGroupId, Function.identity());
    }

    public static ArtifactState empty() {
        return new ArtifactState(HashMultimap.withSeq().empty());
    }

    public Map<String, Group> getGroupByName() {
        return this.groupByName;
    }

    public Map<UUID, Group> getGroupById() {
        return this.groupById;
    }

    public Multimap<Group, Artifact> getGroupArtifacts() {
        return this.groupArtifacts;
    }

}
