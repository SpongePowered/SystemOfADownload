package org.spongepowered.synchronizer.versionsync;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;

@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(SyncRegistration.Register.class),
    @JsonSubTypes.Type(SyncRegistration.SyncBatch.class),
    @JsonSubTypes.Type(SyncRegistration.AlreadyRegistered.class),
    @JsonSubTypes.Type(SyncRegistration.Timeout.class),
    @JsonSubTypes.Type(SyncRegistration.GroupUnregistered.class),
    @JsonSubTypes.Type(SyncRegistration.MarkRegistered.class),
    @JsonSubTypes.Type(SyncRegistration.Refresh.class),
    @JsonSubTypes.Type(SyncRegistration.RetryFailed.class)
})
public sealed interface SyncRegistration extends Jsonable {

    @JsonTypeName("register")
    record Register(MavenCoordinates coordinates) implements SyncRegistration {
    }

    @JsonTypeName("register-batch")
    record SyncBatch(ArtifactCoordinates artifact, List<MavenCoordinates> coordinates, akka.actor.typed.ActorRef<akka.Done> replyTo) implements SyncRegistration {

    }

    @JsonTypeName("stop-batch")
    enum Timeout implements SyncRegistration {
        INSTANCE;
    }

    @JsonTypeName("update-batch")
    enum Refresh implements SyncRegistration {
        INSTANCE;
    }

    @JsonTypeName("duplicate-registration")
    record AlreadyRegistered(MavenCoordinates coordinates) implements SyncRegistration {
    }

    @JsonTypeName("failed-registration")
    record RetryFailed(MavenCoordinates coordinates) implements SyncRegistration {
    }

    @JsonTypeName("group-missing")
    record GroupUnregistered(MavenCoordinates coordinates) implements SyncRegistration {
    }

    @JsonTypeName("successful-registration")
    record MarkRegistered(MavenCoordinates coordinates) implements SyncRegistration {
    }
}
