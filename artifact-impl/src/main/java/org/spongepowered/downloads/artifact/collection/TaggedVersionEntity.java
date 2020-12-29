package org.spongepowered.downloads.artifact.collection;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.artifact.api.query.GetTaggedArtifacts;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("unchecked")
public class TaggedVersionEntity
    extends PersistentEntity<TaggedVersionEntity.Command, TaggedVersionEntity.Event, TaggedVersionEntity.TaggedState> {

    @Override
    public Behavior initialBehavior(
        final Optional<TaggedState> snapshotState
    ) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(snapshotState.orElseGet(TaggedState.EmptyState::new));

        builder.setReadOnlyCommandHandler(Command.RequestTaggedVersions.class, this::handleRequestVersions);
        return builder.build();
    }

    private void handleRequestVersions(
        final Command.RequestTaggedVersions cmd,
        final ReadOnlyCommandContext<GetTaggedArtifacts.Response> ctx
    ) {

    }

    public interface Command {
        final static class RequestTaggedVersions implements Command, ReplyType<GetTaggedArtifacts.Response> {
            private final int limit;
            private final int offset;

            public RequestTaggedVersions(
                final int limit,
                final int offset
            ) {
                this.limit = limit;
                this.offset = offset;
            }

            public int limit() {
                return this.limit;
            }

            public int offset() {
                return this.offset;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (RequestTaggedVersions) obj;
                return this.limit == that.limit &&
                    this.offset == that.offset;
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.limit, this.offset);
            }

            @Override
            public String toString() {
                return "RequestTaggedVersions[" +
                    "limit=" + this.limit + ", " +
                    "offset=" + this.offset + ']';
            }
        }

        final static class RegisterTag implements Command, ReplyType<NotUsed> {
            private final String tagVersion;

            public RegisterTag(
                final String tagVersion
            ) {
                this.tagVersion = tagVersion;
            }

            public String tagVersion() {
                return this.tagVersion;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (RegisterTag) obj;
                return Objects.equals(this.tagVersion, that.tagVersion);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.tagVersion);
            }

            @Override
            public String toString() {
                return "RegisterTag[" +
                    "tagVersion=" + this.tagVersion + ']';
            }
        }
    }

    public interface Event extends Jsonable {

        final static class CreatedTaggedVersion implements Event {
            @Serial private static final long serialVersionUID = 0L;
            private final String mavenVersion;
            private final String tag;
            private final String tagValue;

            public CreatedTaggedVersion(final String mavenVersion, final String tag, final String tagValue) {
                this.mavenVersion = mavenVersion;
                this.tag = tag;
                this.tagValue = tagValue;
            }

            public String mavenVersion() {
                return this.mavenVersion;
            }

            public String tag() {
                return this.tag;
            }

            public String tagValue() {
                return this.tagValue;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (CreatedTaggedVersion) obj;
                return Objects.equals(this.mavenVersion, that.mavenVersion) &&
                    Objects.equals(this.tag, that.tag) &&
                    Objects.equals(this.tagValue, that.tagValue);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.mavenVersion, this.tag, this.tagValue);
            }

            @Override
            public String toString() {
                return "CreatedTaggedVersion[" +
                    "mavenVersion=" + this.mavenVersion + ", " +
                    "tag=" + this.tag + ", " +
                    "tagValue=" + this.tagValue + ']';
            }
        }
    }

    public interface TaggedState {

        final static class EmptyState implements TaggedState {
            public EmptyState() {
            }

            @Override
            public boolean equals(final Object obj) {
                return obj == this || obj != null && obj.getClass() == this.getClass();
            }

            @Override
            public int hashCode() {
                return 1;
            }

            @Override
            public String toString() {
                return "EmptyState[]";
            }
        }
    }
}
