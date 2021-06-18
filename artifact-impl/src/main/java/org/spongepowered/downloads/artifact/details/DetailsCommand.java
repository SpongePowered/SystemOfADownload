package org.spongepowered.downloads.artifact.details;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.query.GetArtifactDetailsResponse;

public interface DetailsCommand extends Jsonable {

    final class GetArtifactDetails implements DetailsCommand {
        public final String artifactId;
        public final ActorRef<GetArtifactDetailsResponse> replyTo;

        public GetArtifactDetails(String artifactId, ActorRef<GetArtifactDetailsResponse> replyTo) {
            this.artifactId = artifactId;
            this.replyTo = replyTo;
        }
    }

    final class RegisterArtifact implements DetailsCommand {
        public final ArtifactCoordinates coordinates;
        public final String displayName;
        public final ActorRef<NotUsed> replyTo;

        public RegisterArtifact(final ArtifactCoordinates coordinates, final String displayName, final ActorRef<NotUsed> replyTo) {
            this.coordinates = coordinates;
            this.displayName = displayName;
            this.replyTo = replyTo;
        }
    }
}
