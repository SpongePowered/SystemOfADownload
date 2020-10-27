package org.spongepowered.downloads.artifact.api.query;

import org.spongepowered.downloads.artifact.api.Group;

public sealed interface GroupResponse {

    final record Missing(String groupId) implements GroupResponse {}

    final record Available(Group group) implements GroupResponse {}

}
