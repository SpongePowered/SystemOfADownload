package org.spongepowered.downloads.artifact.api.registration;

import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.Group;

public sealed interface GroupRegistrationResponse
    extends Jsonable
    permits GroupRegistrationResponse.GroupAlreadyRegistered,
        GroupRegistrationResponse.GroupRegistered {

    final record GroupAlreadyRegistered(
        String groupNameRequested
    ) implements GroupRegistrationResponse {
    }

    final record GroupRegistered(
        Group group
    ) implements GroupRegistrationResponse {
    }
}
