package org.spongepowered.downloads.artifact;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import org.spongepowered.downloads.artifact.event.ArtifactEvent;

import java.util.Optional;

public class ArtifactEntity extends PersistentEntity<ArtifactCommand, ArtifactEvent, ArtifactState> {
    @SuppressWarnings("unchecked")
    @Override
    public Behavior initialBehavior(Optional<ArtifactState> snapshotState) {
        final var builder = this.newBehaviorBuilder(snapshotState.orElseGet(ArtifactState::empty));

        return builder.build();
    }
}
