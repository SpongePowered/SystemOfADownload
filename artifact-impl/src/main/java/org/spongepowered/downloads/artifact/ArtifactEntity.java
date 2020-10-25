package org.spongepowered.downloads.artifact;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.collection.List;
import io.vavr.collection.Multimap;
import io.vavr.collection.Traversable;
import io.vavr.control.Either;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistrationResponse;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;
import org.spongepowered.downloads.artifact.event.ArtifactEvent;

import java.util.Optional;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class ArtifactEntity extends PersistentEntity<ArtifactCommand, ArtifactEvent, ArtifactState> {

    @Override
    public Behavior initialBehavior(final Optional<ArtifactState> snapshotState) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(snapshotState.orElseGet(ArtifactState::empty));

        builder.setReadOnlyCommandHandler(ArtifactCommand.GetArtifacts.class, this::handleGetArtifactsCommand);

        builder.setCommandHandler(ArtifactCommand.RegisterArtifactCommand.class, this::handleRegisterArtifactCommand);
        builder.setEventHandler(ArtifactEvent.ArtifactRegistered.class, this::handleRegisterArtifactEvent);

        return builder.build();
    }

    private void handleGetArtifactsCommand(
        final ArtifactCommand.GetArtifacts cmd,
        final ReadOnlyCommandContext<GetArtifactsResponse> ctx
    ) {
        ctx.reply(this.state().getGroupByName().get(cmd.groupId())
            .toEither(() -> new GetArtifactsResponse.GroupMissing(cmd.groupId()))
            .map(group -> this.state().getGroupArtifacts().getOrElse(group, List.empty()))
            .map(artifactsTraversable -> List.ofAll(artifactsTraversable.iterator()))
            .map(GetArtifactsResponse.ArtifactsAvailable::new)
            .fold(Function.identity(), Function.identity()));
    }

    private Persist<ArtifactEvent> handleRegisterArtifactCommand(
        final ArtifactCommand.RegisterArtifactCommand cmd,
        final CommandContext<ArtifactRegistrationResponse> ctx
    ) {
        ctx.reply(this.state().getGroupByName().get(cmd.groupId())
            .toEither(() -> (ArtifactRegistrationResponse) new ArtifactRegistrationResponse.GroupMissing(cmd.groupId()))
            .flatMap(group -> {
                final Traversable<Artifact> filtered = this.state().getGroupArtifacts()
                    .getOrElse(group, List.empty())
                    .filter(artifact ->
                        artifact.getArtifactId().equals(cmd.artifactId())
                        && !artifact.getVersion().equals(cmd.version())
                    );

                if (filtered.isEmpty()) {
                    final Artifact newArtifact = new Artifact(group, cmd.artifactId(), cmd.version());

                    // Aha! We can now register a new artifact to our state!
                    ctx.thenPersist(new ArtifactEvent.ArtifactRegistered(newArtifact));
                    return Either.right(new ArtifactRegistrationResponse.RegisteredArtifact(newArtifact));
                }
                return Either.right(new ArtifactRegistrationResponse.ArtifactAlreadyRegistered(
                    cmd.artifactId(),
                    group.getGroupCoordinates()
                ));
            }).fold(Function.identity(), Function.identity()));

        return ctx.done();
    }

    private ArtifactState handleRegisterArtifactEvent(final ArtifactEvent.ArtifactRegistered event) {
        final Multimap<Group, Artifact> existing = this.state().getGroupArtifacts();
        final Group targetGroup = event.artifact().getGroup();
        return new ArtifactState(existing.put(targetGroup, event.artifact()));
    }
}
