package org.spongepowered.downloads.webhook;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

import java.util.Optional;

public class ArtifactProcessorEntity extends PersistentEntity<ArtifactProcessorEntity, ArtifactProcessingEvent, ArtifactProcessingState> {


    @Override
    public Behavior initialBehavior(
        final Optional<ArtifactProcessingState> snapshotState
    ) {
        return null;
    }
}
