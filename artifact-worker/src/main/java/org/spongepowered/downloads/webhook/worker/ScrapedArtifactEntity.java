package org.spongepowered.downloads.webhook.worker;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.git.api.CommitSha;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;
import org.spongepowered.downloads.webhook.sonatype.Component;

import java.util.Optional;

public class ScrapedArtifactEntity extends PersistentEntity<ScrapedArtifactEntity.Command, ScrapedArtifactEvent, ScrapedArtifactEntity.State> {

    @Override
    public Behavior initialBehavior(
        final Optional<State> snapshotState
    ) {
        final Behavior behavior = this.newBehavior(snapshotState.orElseGet(State::new));
        return behavior;
    }

    static sealed interface Command {
        final record AssociateMetadataWithCollection(
            ArtifactCollection collection,
            Component component,
            String tagVersion
        ) implements Command, ReplyType<NotUsed> {
        }


        final record RequestArtifactForProcessing(String groupId, String artifactId, String requested) implements Command, ReplyType<NotUsed> {
        }
        final record AssociateCommitShaWithArtifact(
            ArtifactCollection collection,
            CommitSha sha
        ) implements Command, ReplyType<NotUsed> {
        }
    }

    static class State {

    }
}
