package org.spongepowered.downloads.webhook;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.git.api.CommitSha;

import java.io.Serial;
import java.util.Objects;

public interface ScrapedArtifactEvent extends AggregateEvent<ScrapedArtifactEvent>, Jsonable {

    AggregateEventTag<ScrapedArtifactEvent> TAG = AggregateEventTag.of(ScrapedArtifactEvent.class);

    @Override
    default AggregateEventTagger<ScrapedArtifactEvent> aggregateTag() {
        return TAG;
    }

    String mavenCoordinates();

    final static class InitializeArtifactForProcessing implements ScrapedArtifactEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final String mavenCoordinates;
        private final String repository;
        private final String componentId;

        public InitializeArtifactForProcessing(
            final String mavenCoordinates,
            final String repository,
            final String componentId
        ) {
            this.mavenCoordinates = mavenCoordinates;
            this.repository = repository;
            this.componentId = componentId;
        }

        public String mavenCoordinates() {
            return this.mavenCoordinates;
        }

        public String repository() {
            return this.repository;
        }

        public String componentId() {
            return this.componentId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (InitializeArtifactForProcessing) obj;
            return Objects.equals(this.mavenCoordinates, that.mavenCoordinates) &&
                Objects.equals(this.repository, that.repository) &&
                Objects.equals(this.componentId, that.componentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.mavenCoordinates, this.repository, this.componentId);
        }

        @Override
        public String toString() {
            return "InitializeArtifactForProcessing[" +
                "mavenCoordinates=" + this.mavenCoordinates + ", " +
                "repository=" + this.repository + ", " +
                "componentId=" + this.componentId + ']';
        }

    }

    final static class ArtifactRequested implements ScrapedArtifactEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final String mavenGroupId;
        private final String mavenArtifactId;
        private final String componentVersion;
        private final String mavenCoordinates;

        public ArtifactRequested(
            final String mavenGroupId,
            final String mavenArtifactId,
            final String componentVersion,
            final String mavenCoordinates
        ) {
            this.mavenGroupId = mavenGroupId;
            this.mavenArtifactId = mavenArtifactId;
            this.componentVersion = componentVersion;
            this.mavenCoordinates = mavenCoordinates;
        }

        public String mavenGroupId() {
            return this.mavenGroupId;
        }

        public String mavenArtifactId() {
            return this.mavenArtifactId;
        }

        public String componentVersion() {
            return this.componentVersion;
        }

        public String mavenCoordinates() {
            return this.mavenCoordinates;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (ArtifactRequested) obj;
            return Objects.equals(this.mavenGroupId, that.mavenGroupId) &&
                Objects.equals(this.mavenArtifactId, that.mavenArtifactId) &&
                Objects.equals(this.componentVersion, that.componentVersion) &&
                Objects.equals(this.mavenCoordinates, that.mavenCoordinates);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.mavenGroupId, this.mavenArtifactId, this.componentVersion, this.mavenCoordinates);
        }

        @Override
        public String toString() {
            return "ArtifactRequested[" +
                "mavenGroupId=" + this.mavenGroupId + ", " +
                "mavenArtifactId=" + this.mavenArtifactId + ", " +
                "componentVersion=" + this.componentVersion + ", " +
                "mavenCoordinates=" + this.mavenCoordinates + ']';
        }

    }

    final static class AssociatedMavenMetadata
        implements ScrapedArtifactEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final ArtifactCollection collection;
        private final String mavenCoordinates;
        private final String tagVersion;
        private final Map<String, Tuple2<String, String>> artifactPathToSonatypeId;

        public AssociatedMavenMetadata(
            final ArtifactCollection collection,
            final String mavenCoordinates,
            final String tagVersion, final Map<String, Tuple2<String, String>> artifactPathToSonatypeId
        ) {
            this.collection = collection;
            this.mavenCoordinates = mavenCoordinates;
            this.tagVersion = tagVersion;
            this.artifactPathToSonatypeId = artifactPathToSonatypeId;
        }

        public ArtifactCollection collection() {
            return this.collection;
        }

        public String mavenCoordinates() {
            return this.mavenCoordinates;
        }

        public String tagVersion() {
            return this.tagVersion;
        }

        public Map<String, Tuple2<String, String>> artifactPathToSonatypeId() {
            return this.artifactPathToSonatypeId;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (AssociatedMavenMetadata) obj;
            return Objects.equals(this.collection, that.collection) &&
                Objects.equals(this.mavenCoordinates, that.mavenCoordinates) &&
                Objects.equals(this.tagVersion, that.tagVersion) &&
                Objects.equals(this.artifactPathToSonatypeId, that.artifactPathToSonatypeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.collection, this.mavenCoordinates, this.tagVersion, this.artifactPathToSonatypeId);
        }

        @Override
        public String toString() {
            return "AssociatedMavenMetadata[" +
                "collection=" + this.collection + ", " +
                "mavenCoordinates=" + this.mavenCoordinates + ", " +
                "tagVersion=" + this.tagVersion + ", " +
                "artifactPathToSonatypeId=" + this.artifactPathToSonatypeId + ']';
        }

    }

    final static class AssociateCommitSha implements ScrapedArtifactEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final ArtifactCollection collection;
        private final String mavenCoordinates;
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final CommitSha commit;

        public AssociateCommitSha(
            final ArtifactCollection collection,
            final String mavenCoordinates,
            final String groupId,
            final String artifactId,
            final String version,
            final CommitSha commit
        ) {
            this.collection = collection;
            this.mavenCoordinates = mavenCoordinates;
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.commit = commit;
        }

        public ArtifactCollection collection() {
            return this.collection;
        }

        public String mavenCoordinates() {
            return this.mavenCoordinates;
        }

        public String groupId() {
            return this.groupId;
        }

        public String artifactId() {
            return this.artifactId;
        }

        public String version() {
            return this.version;
        }

        public CommitSha commit() {
            return this.commit;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final var that = (AssociateCommitSha) obj;
            return Objects.equals(this.collection, that.collection) &&
                Objects.equals(this.mavenCoordinates, that.mavenCoordinates) &&
                Objects.equals(this.groupId, that.groupId) &&
                Objects.equals(this.artifactId, that.artifactId) &&
                Objects.equals(this.version, that.version) &&
                Objects.equals(this.commit, that.commit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                this.collection, this.mavenCoordinates, this.groupId, this.artifactId, this.version, this.commit);
        }

        @Override
        public String toString() {
            return "AssociateCommitSha[" +
                "collection=" + this.collection + ", " +
                "mavenCoordinates=" + this.mavenCoordinates + ", " +
                "groupId=" + this.groupId + ", " +
                "artifactId=" + this.artifactId + ", " +
                "version=" + this.version + ", " +
                "commit=" + this.commit + ']';
        }


    }
}
