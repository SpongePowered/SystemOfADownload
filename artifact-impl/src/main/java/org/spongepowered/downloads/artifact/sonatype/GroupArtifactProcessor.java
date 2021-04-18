package org.spongepowered.downloads.artifact.sonatype;

import akka.Done;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession;
import org.pcollections.PSequence;
import org.spongepowered.downloads.artifact.group.GroupEvent;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

public class GroupArtifactProcessor extends ReadSideProcessor<GroupEvent> {

    private final CassandraReadSide readSide;
    private final CassandraSession session;

    @Inject
    public GroupArtifactProcessor(
        final CassandraReadSide readSide, final CassandraSession session
    ) {
        this.readSide = readSide;
        this.session = session;
    }

    @Override
    public ReadSideHandler<GroupEvent> buildHandler() {
        return readSide.builder("")
    }

    @Override
    public PSequence<AggregateEventTag<GroupEvent>> aggregateTags() {
        return GroupEvent.TAG.allTags();
    }

    private CompletionStage<Done> createTable() {
        return session.executeCreateTable(
            "CREATE TABLE IF NOT EXISTS scheduledArtifacts ( " +
                "groupId text," +
                "artifactId text," +
                "lastChecked timestamp," +
                "lastNexusUpdate timestamp," +
                "PRIMARY KEY (groupId, artifactId)" +
                ")").thenCompose(d -> session.executeCreateTable(
                    "CREATE INDEX IF NOT EXISTS scheduledArtifactsIndex " +
            "on scheduledArtifacts (groupId, artifactId, lastChecked)")
        );
    }
}
