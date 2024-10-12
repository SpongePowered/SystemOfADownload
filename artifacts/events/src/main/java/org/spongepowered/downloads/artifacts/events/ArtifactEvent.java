package org.spongepowered.downloads.artifacts.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.events.EventMarker;
import org.spongepowered.downloads.events.KafkaEvent;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface ArtifactEvent extends EventMarker {

    ArtifactCoordinates coordinates();

    default String partitionKey() {
        return this.coordinates().asMavenString();
    }

    default String topic() {
        return "ArtifactsArtifactUpserted";
    }

    @JsonTypeName("registered")
    @JsonDeserialize
    record ArtifactRegistered(
        ArtifactCoordinates coordinates
    ) implements ArtifactEvent {

        @JsonCreator
        public ArtifactRegistered {
        }
    }

    @JsonTypeName("git-repository")
    @JsonDeserialize
    record GitRepositoryAssociated(
        ArtifactCoordinates coordinates,
        String repository
    ) implements ArtifactEvent {

        @JsonCreator
        public GitRepositoryAssociated {
        }
    }

    @JsonTypeName("website")
    @JsonDeserialize
    record WebsiteUpdated(
        ArtifactCoordinates coordinates,
        String url
    ) implements ArtifactEvent {

        @JsonCreator
        public WebsiteUpdated {
        }
    }

    @JsonTypeName("issues")
    @JsonDeserialize
    record IssuesUpdated(
        ArtifactCoordinates coordinates,
        String url
    ) implements ArtifactEvent {

        @JsonCreator
        public IssuesUpdated {
        }
    }

    @JsonTypeName("displayName")
    @JsonDeserialize
    record DisplayNameUpdated(
        ArtifactCoordinates coordinates,
        String displayName
    ) implements ArtifactEvent {

        @JsonCreator
        public DisplayNameUpdated {
        }
    }
}
