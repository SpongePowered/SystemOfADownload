/*
 * This file is part of SystemOfADownload, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://spongepowered.org/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.downloads.webhook.worker;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.Tuple2;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCollection;
import org.spongepowered.downloads.git.api.CommitSha;
import org.spongepowered.downloads.webhook.ScrapedArtifactEvent;
import org.spongepowered.downloads.webhook.sonatype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

@SuppressWarnings("unchecked")
public class ScrapedArtifactEntity extends PersistentEntity<ScrapedArtifactEntity.Command, ScrapedArtifactEvent, ScrapedArtifactEntity.ProcessingState> {

    @Override
    public Behavior initialBehavior(
        final Optional<ProcessingState> snapshotState
    ) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(snapshotState.orElseGet(ProcessingState.EmptyState::new));

        builder.setCommandHandler(Command.RequestArtifactForProcessing.class, this::respondRequestArtifactForProcessing);
        builder.setCommandHandler(Command.AssociateCommitShaWithArtifact.class, this::respondToAssociatingCommitShaWithArtifact);
        return builder.build();
    }

    static interface Command {
        final static class AssociateMetadataWithCollection implements Command, ReplyType<NotUsed> {
            private final ArtifactCollection collection;
            private final Component component;
            private final String tagVersion;

            public AssociateMetadataWithCollection(
                final ArtifactCollection collection,
                final Component component,
                final String tagVersion
            ) {
                this.collection = collection;
                this.component = component;
                this.tagVersion = tagVersion;
            }

            public ArtifactCollection collection() {
                return this.collection;
            }

            public Component component() {
                return this.component;
            }

            public String tagVersion() {
                return this.tagVersion;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (AssociateMetadataWithCollection) obj;
                return Objects.equals(this.collection, that.collection) &&
                    Objects.equals(this.component, that.component) &&
                    Objects.equals(this.tagVersion, that.tagVersion);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.collection, this.component, this.tagVersion);
            }

            @Override
            public String toString() {
                return "AssociateMetadataWithCollection[" +
                    "collection=" + this.collection + ", " +
                    "component=" + this.component + ", " +
                    "tagVersion=" + this.tagVersion + ']';
            }

        }


        final static class RequestArtifactForProcessing implements Command, ReplyType<NotUsed> {
            private final String groupId;
            private final String artifactId;
            private final String requested;

            public RequestArtifactForProcessing(final String groupId, final String artifactId, final String requested) {
                this.groupId = groupId;
                this.artifactId = artifactId;
                this.requested = requested;
            }

            public String groupId() {
                return this.groupId;
            }

            public String artifactId() {
                return this.artifactId;
            }

            public String requested() {
                return this.requested;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (RequestArtifactForProcessing) obj;
                return Objects.equals(this.groupId, that.groupId) &&
                    Objects.equals(this.artifactId, that.artifactId) &&
                    Objects.equals(this.requested, that.requested);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.groupId, this.artifactId, this.requested);
            }

            @Override
            public String toString() {
                return "RequestArtifactForProcessing[" +
                    "groupId=" + this.groupId + ", " +
                    "artifactId=" + this.artifactId + ", " +
                    "requested=" + this.requested + ']';
            }

        }

        final static class AssociateCommitShaWithArtifact implements Command, ReplyType<NotUsed> {
            private final ArtifactCollection collection;
            private final CommitSha sha;

            public AssociateCommitShaWithArtifact(
                final ArtifactCollection collection,
                final CommitSha sha
            ) {
                this.collection = collection;
                this.sha = sha;
            }

            public ArtifactCollection collection() {
                return this.collection;
            }

            public CommitSha sha() {
                return this.sha;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (AssociateCommitShaWithArtifact) obj;
                return Objects.equals(this.collection, that.collection) &&
                    Objects.equals(this.sha, that.sha);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.collection, this.sha);
            }

            @Override
            public String toString() {
                return "AssociateCommitShaWithArtifact[" +
                    "collection=" + this.collection + ", " +
                    "sha=" + this.sha + ']';
            }

        }
    }

    interface ProcessingState {

        boolean hasStarted();

        boolean hasMetadata();

        boolean hasCommit();

        boolean hasCompleted();

        Optional<String> getCoordinates();

        Optional<String> getRepository();

        default Optional<String> getArtifactId() {
            return this.getCoordinates().map(coords -> coords.split(":")[1]);
        }

        default Optional<String> getGroupId() {
            return this.getCoordinates().map(coords -> coords.split(":")[0]);
        }

        default Optional<String> getMavenVersion() {
            return this.getCoordinates().map(coords -> coords.split(":")[2]);
        }

        Optional<Map<String, Tuple2<String, String>>> getArtifacts();

        static final class EmptyState implements ProcessingState {
            public EmptyState() {
            }

            public static EmptyState empty() {
                return new EmptyState();
            }

            @Override
            public boolean hasStarted() {
                return false;
            }

            @Override
            public boolean hasMetadata() {
                return false;
            }

            @Override
            public boolean hasCommit() {
                return false;
            }

            @Override
            public boolean hasCompleted() {
                return false;
            }

            @Override
            public Optional<String> getCoordinates() {
                return Optional.empty();
            }

            @Override
            public Optional<String> getRepository() {
                return Optional.empty();
            }

            @Override
            public Optional<Map<String, Tuple2<String, String>>> getArtifacts() {
                return Optional.empty();
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

        static final class MetadataState implements ProcessingState {
            private final String coordinates;
            private final String repository;
            private final Map<String, Tuple2<String, String>> artifacts;

            public MetadataState(
                final String coordinates,
                final String repository,
                final Map<String, Tuple2<String, String>> artifacts
            ) {
                this.coordinates = coordinates;
                this.repository = repository;
                this.artifacts = artifacts;
            }

            @Override
            public boolean hasStarted() {
                return true;
            }

            @Override
            public boolean hasMetadata() {
                return true;
            }

            @Override
            public boolean hasCommit() {
                return false;
            }

            @Override
            public boolean hasCompleted() {
                return false;
            }

            @Override
            public Optional<String> getRepository() {
                return Optional.of(this.repository());
            }

            @Override
            public Optional<String> getCoordinates() {
                return Optional.of(this.coordinates);
            }

            @Override
            public Optional<Map<String, Tuple2<String, String>>> getArtifacts() {
                return Optional.of(this.artifacts);
            }

            public String coordinates() {
                return this.coordinates;
            }

            public String repository() {
                return this.repository;
            }

            public Map<String, Tuple2<String, String>> artifacts() {
                return this.artifacts;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (MetadataState) obj;
                return Objects.equals(this.coordinates, that.coordinates) &&
                    Objects.equals(this.repository, that.repository) &&
                    Objects.equals(this.artifacts, that.artifacts);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.coordinates, this.repository, this.artifacts);
            }

            @Override
            public String toString() {
                return "MetadataState[" +
                    "coordinates=" + this.coordinates + ", " +
                    "repository=" + this.repository + ", " +
                    "artifacts=" + this.artifacts + ']';
            }

        }

        static final class CommittedState
            implements ProcessingState {
            private final String s;
            private final String repository;
            private final Map<String, Tuple2<String, String>> artifacts;
            private final CommitSha commit;

            public CommittedState(
                final String s, final String repository, final Map<String, Tuple2<String, String>> artifacts, final CommitSha commit
            ) {
                this.s = s;
                this.repository = repository;
                this.artifacts = artifacts;
                this.commit = commit;
            }

            @Override
            public boolean hasStarted() {
                return true;
            }

            @Override
            public boolean hasMetadata() {
                return true;
            }

            @Override
            public boolean hasCommit() {
                return true;
            }

            @Override
            public boolean hasCompleted() {
                return false;
            }

            @Override
            public Optional<String> getCoordinates() {
                return Optional.empty();
            }

            @Override
            public Optional<String> getRepository() {
                return Optional.of(this.repository());
            }

            @Override
            public Optional<Map<String, Tuple2<String, String>>> getArtifacts() {
                return Optional.of(this.artifacts);
            }

            public String s() {
                return this.s;
            }

            public String repository() {
                return this.repository;
            }

            public Map<String, Tuple2<String, String>> artifacts() {
                return this.artifacts;
            }

            public CommitSha commit() {
                return this.commit;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                final var that = (CommittedState) obj;
                return Objects.equals(this.s, that.s) &&
                    Objects.equals(this.repository, that.repository) &&
                    Objects.equals(this.artifacts, that.artifacts) &&
                    Objects.equals(this.commit, that.commit);
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.s, this.repository, this.artifacts, this.commit);
            }

            @Override
            public String toString() {
                return "CommittedState[" +
                    "s=" + this.s + ", " +
                    "repository=" + this.repository + ", " +
                    "artifacts=" + this.artifacts + ", " +
                    "commit=" + this.commit + ']';
            }

        }

    }


    private Persist<ScrapedArtifactEvent> respondToAssociatingCommitShaWithArtifact(
        final Command.AssociateCommitShaWithArtifact cmd,
        final CommandContext<NotUsed> ctx) {
        if (!this.state().hasCommit()) {
            ctx.thenPersist(new ScrapedArtifactEvent.AssociateCommitSha(
                cmd.collection,
                cmd.collection.getMavenCoordinates(),
                cmd.collection.getGroup().getGroupCoordinates(),
                cmd.collection.getArtifactId(),
                cmd.collection.getVersion(),
                cmd.sha
            ));
            return ctx.done();
        }
        return ctx.done();
    }

    private Persist<ScrapedArtifactEvent> respondRequestArtifactForProcessing(
        final Command.RequestArtifactForProcessing cmd,
        final CommandContext<NotUsed> ctx
    ) {
        final String mavenCoordinates = new StringJoiner(":").add(cmd.groupId).add(cmd.artifactId).add(cmd.requested).toString();

        if (this.state().getCoordinates().map(coords -> !coords.equals(mavenCoordinates)).orElse(true)) {
            ctx.thenPersist(
                new ScrapedArtifactEvent.ArtifactRequested(cmd.groupId, cmd.artifactId, cmd.requested, mavenCoordinates),
                message -> ctx.reply(NotUsed.notUsed())
            );
        }
        return ctx.done();
    }
}
