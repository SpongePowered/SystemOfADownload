package org.spongepowered.downloads.artifact.group.state;

import org.spongepowered.downloads.artifact.api.Group;

public final class EmptyState implements GroupState {
    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Group asGroup() {
        return new Group("", "", "");
    }

    @Override
    public String website() {
        return "null";
    }

    @Override
    public String name() {
        return "null";
    }

    @Override
    public String groupCoordinates() {
        return "null";
    }
}
