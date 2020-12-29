package org.spongepowered.downloads.artifact.api.query;

import com.lightbend.lagom.serialization.Jsonable;
import io.swagger.v3.oas.annotations.media.Schema;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;

public final class ArtifactRegistration {

    public static final record RegisterCollection(@Schema(required = true) ArtifactCollection collection) {}

    public sealed interface Response extends Jsonable {

        final record ArtifactAlreadyRegistered(String artifactName, String groupId) implements
            Response { }

        final record RegisteredArtifact(ArtifactCollection artifact) implements Response {
            public String getMavenCoordinates() {
                return this.artifact.getMavenCoordinates();
            }
        }

        final record GroupMissing(String s) implements Response { }

    }
}
