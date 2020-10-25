package org.spongepowered.downloads.changelog;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.collection.List;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.event.ArtifactEvent;
import org.spongepowered.downloads.artifact.event.ChangelogEvent;
import org.spongepowered.downloads.changelog.api.query.ChangelogResponse;

import java.util.Optional;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class ChangelogEntity extends PersistentEntity<ChangelogCommand, ChangelogEvent, ChangelogState> {

    private static final Logger LOGGER = LogManager.getLogger(ChangelogEntity.class);

    @Override
    public Behavior initialBehavior(final Optional<ChangelogState> snapshotState) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(snapshotState.orElseGet(ChangelogState::empty));

        // Registering Artifacts (because the read side persistence for Artifact registration will read in
        // new Artifacts, but we effectively will have workers that do some saga work
        // like pulling the jar, associating the commit sha with the artifact, etc.
        builder.setCommandHandler(ChangelogCommand.RegisterArtifact.class, this::processRegisterArtifact);
        builder.setEventHandler(ChangelogEvent.ArtifactRegistered.class, this::handleRegisterArtifact);

        return builder.build();
    }

    private Persist<ChangelogEvent> processRegisterArtifact(
        final ChangelogCommand.RegisterArtifact cmd,
        final CommandContext<NotUsed> ctx
    ) {
        if (this.state().getCoordinates().isEmpty()) {
            ctx.thenPersist(new ChangelogEvent.ArtifactRegistered(cmd.artifact()));
        }
        return ctx.done();
    }

    private ChangelogState handleRegisterArtifact(final ChangelogEvent.ArtifactRegistered event) {
        return new ChangelogState(event.artifact(), List.empty());
    }

}
