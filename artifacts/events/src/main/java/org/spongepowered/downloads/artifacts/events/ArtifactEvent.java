package org.spongepowered.downloads.artifacts.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.spongepowered.downloads.akka.AkkaSerializable;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface ArtifactEvent extends AkkaSerializable {

    ArtifactCoordinates coordinates();

    default String partitionKey() {
        return this.coordinates().asMavenString();
    }

    @JsonTypeName("registered")
    @JsonDeserialize
    final record ArtifactRegistered(
        ArtifactCoordinates coordinates
    ) implements ArtifactEvent {

        @JsonCreator
        public ArtifactRegistered {
        }
    }

    @JsonTypeName("git-repository")
    @JsonDeserialize
    final record GitRepositoryAssociated(
        ArtifactCoordinates coordinates,
        String repository
    ) implements ArtifactEvent {

        @JsonCreator
        public GitRepositoryAssociated {
        }
    }

    @JsonTypeName("website")
    @JsonDeserialize
    final record WebsiteUpdated(
        ArtifactCoordinates coordinates,
        String url
    ) implements ArtifactEvent {

        @JsonCreator
        public WebsiteUpdated {
        }
    }

    @JsonTypeName("issues")
    @JsonDeserialize
    final record IssuesUpdated(
        ArtifactCoordinates coordinates,
        String url
    ) implements ArtifactEvent {

        @JsonCreator
        public IssuesUpdated {
        }
    }

    @JsonTypeName("displayName")
    @JsonDeserialize
    final record DisplayNameUpdated(
        ArtifactCoordinates coordinates,
        String displayName
    ) implements ArtifactEvent {

        @JsonCreator
        public DisplayNameUpdated {
        }
    }
}
