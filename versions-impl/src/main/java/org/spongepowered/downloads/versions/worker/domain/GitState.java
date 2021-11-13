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
package org.spongepowered.downloads.versions.worker.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(GitState.RepositoryAssociated.class),
    @JsonSubTypes.Type(GitState.Registered.class)
})
@JsonDeserialize
public interface GitState extends Jsonable {

    boolean hasRepo();

    GitState EMPTY = new Empty();

    static GitState empty() {
        return GitState.EMPTY;
    }

    final record Empty() implements GitState {
        @Override
        public boolean hasRepo() {
            return false;
        }
    }

    @JsonTypeName("PrimitiveState")
    final record Registered(
        ArtifactCoordinates coordinates,
        Set<String> versions
    ) implements GitState {

        @JsonCreator
        public Registered {
        }

        @Override
        public boolean hasRepo() {
            return false;
        }

        public GitState acceptVersion(MavenCoordinates coordinates) {
            return new Registered(this.coordinates, this.versions.add(coordinates.version));
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(Unchecked.class),
        @JsonSubTypes.Type(Commit.class),
        @JsonSubTypes.Type(Processed.class)
    })
    interface CommitState extends Jsonable {

        default boolean isProcessed() {
            return false;
        }
    }

    @JsonTypeName("unchecked")
    final record Unchecked() implements CommitState {
        @JsonCreator
        public Unchecked() {
        }
    }

    @JsonTypeName("committed")
    final record Commit(String sha) implements CommitState {
        @JsonCreator
        public Commit {
        }
    }

    @JsonTypeName("resolved")
    final record Processed(VersionedCommit commit, URI repo) implements CommitState {
        @JsonCreator
        public Processed {
        }
    }

    @JsonTypeName("missing")
    final record Missing() implements CommitState {
        @JsonCreator
        public Missing {
        }

        @Override
        public boolean isProcessed() {
            return true;
        }
    }


    @JsonTypeName("RepositoryAssociated")
    @JsonDeserialize
    final record RepositoryAssociated(
        ArtifactCoordinates coordinates,
        URI gitRepository,
        Map<String, CommitState> commits
    ) implements GitState {

        @JsonCreator
        public RepositoryAssociated {
        }

        @Override
        public boolean hasRepo() {
            return true;
        }

        public GitState appendCommitToVersion(GitEvent.CommitAssociatedWithVersion e) {
            return new RepositoryAssociated(
                this.coordinates,
                this.gitRepository,
                this.commits.put(e.coordinates().version, new Commit(e.sha()))
            );
        }

        public GitState addVersion(GitEvent.VersionRegistered e) {
            return new RepositoryAssociated(
                this.coordinates,
                this.gitRepository,
                this.commits.put(e.coordinates().version, new Unchecked())
            );
        }

        public GitState labelAssetsAsMissingCommit(GitEvent.ArtifactLabeledMissingCommit e) {
            return new RepositoryAssociated(
                this.coordinates,
                this.gitRepository,
                this.commits.put(e.coordinates().version, new Missing())
            );
        }

        public GitState associateCommitDetails(GitEvent.CommitDetailsUpdated e) {
            return new RepositoryAssociated(
                this.coordinates,
                this.gitRepository,
                this.commits.put(e.coordinates().version, new Processed(e.commit(), this.gitRepository))
            );
        }
    }
}
