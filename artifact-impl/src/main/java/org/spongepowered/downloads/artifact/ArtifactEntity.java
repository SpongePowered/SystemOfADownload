package org.spongepowered.downloads.artifact;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Multimap;
import io.vavr.collection.Traversable;
import io.vavr.control.Either;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.artifact.api.query.ArtifactRegistration;
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
        final CommandContext<ArtifactRegistration.Response> ctx
    ) {
        ctx.reply(this.state().getGroupByName().get(cmd.groupId())
            .toEither(() -> (ArtifactRegistration.Response) new ArtifactRegistration.Response.GroupMissing(cmd.groupId()))
            .flatMap(group -> {
                final Traversable<Artifact> filtered = this.state().getGroupArtifacts()
                    .getOrElse(group, List.empty())
                    .filter(artifact ->
                        artifact.getArtifactId().equals(cmd.artifactId())
                        && !artifact.getVersion().equals(cmd.version())
                    );

                if (filtered.isEmpty()) {
                    final Artifact newArtifact = new Artifact(group, cmd.artifactId(), cmd.version(), "");
                    ctx.thenPersist(new ArtifactEvent.ArtifactRegistered(newArtifact));
                    final var collection = new ArtifactCollection(HashMap.empty(), group, cmd.artifactId(), cmd.version());
                    return Either.right(new ArtifactRegistration.Response.RegisteredArtifact(collection));
                }
                return Either.right(new ArtifactRegistration.Response.ArtifactAlreadyRegistered(
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
