package org.spongepowered.downloads.artifact.api.query;

import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.Group;

import java.net.URL;

public final class GroupRegistration {

    public static final record RegisterGroupRequest(
        String groupName,
        String groupCoordinates,
        String website
    ) { }

    public sealed interface Response extends Jsonable {

        final record GroupAlreadyRegistered(String groupNameRequested) implements Response { }

        final record GroupRegistered(Group group) implements Response { }
    }
}
