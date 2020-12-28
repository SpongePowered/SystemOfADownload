package org.spongepowered.downloads.webhook;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.git.api.CommitSha;

public sealed interface ScrapedArtifactEvent extends AggregateEvent<ScrapedArtifactEvent>, Jsonable {

    AggregateEventTag<ScrapedArtifactEvent> TAG = AggregateEventTag.of(ScrapedArtifactEvent.class);

    @Override
    default AggregateEventTagger<ScrapedArtifactEvent> aggregateTag() {
        return TAG;
    }

    String mavenCoordinates();

    final record InitializeArtifactForProcessing(
        String mavenCoordinates,
        String repository,
        String componentId
    ) implements ScrapedArtifactEvent {
    }

    final record ArtifactRequested(
        String mavenGroupId,
        String mavenArtifactId,
        String componentVersion,
        String mavenCoordinates
    ) implements ScrapedArtifactEvent {
    }

    final record AssociatedMavenMetadata(
        ArtifactCollection collection,
        String mavenCoordinates,
        String tagVersion, Map<String, Tuple2<String, String>> artifactPathToSonatypeId
    )
        implements ScrapedArtifactEvent {
    }

    final record AssociateCommitSha(
        ArtifactCollection collection,
        String mavenCoordinates,
        String groupId,
        String artifactId,
        String version,
        CommitSha commit
    ) implements ScrapedArtifactEvent {

    }
}
