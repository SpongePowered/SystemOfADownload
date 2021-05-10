package org.spongepowered.downloads.versions.sonatype.global;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

public interface ManageCommand extends Jsonable {

    @JsonSerialize
    final class Add implements ManageCommand {

        public final ArtifactCoordinates coordinates;
        public final ActorRef<NotUsed> replyTo;

        public Add(final ArtifactCoordinates coordinates, final ActorRef<NotUsed> replyTo) {
            this.coordinates = coordinates;
            this.replyTo = replyTo;
        }
    }

    final class GetAllArtifacts implements ManageCommand {
        public final ActorRef<List<ArtifactCoordinates>> replyTo;

        public GetAllArtifacts(
            final ActorRef<List<ArtifactCoordinates>> replyTo
        ) {
            this.replyTo = replyTo;
        }
    }
}
