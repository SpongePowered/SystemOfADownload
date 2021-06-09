package org.spongepowered.downloads.artifact.global;

import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.Group;

import java.util.Objects;

public final class GlobalState {

    public final List<Group> groups;

    public GlobalState() {
        this.groups = List.empty();
    }

    public GlobalState(final List<Group> groups) {
        this.groups = Objects.requireNonNull(groups);
    }
}
