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
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(GitEvent.ArtifactRegistered.class),
    @JsonSubTypes.Type(GitEvent.RepoRegistered.class),
    @JsonSubTypes.Type(GitEvent.CommitAssociatedWithVersion.class),
    @JsonSubTypes.Type(GitEvent.VersionRegistered.class)
})
public interface GitEvent extends AggregateEvent<GitEvent>, Jsonable {

    AggregateEventShards<GitEvent> INSTANCE = AggregateEventTag.sharded(GitEvent.class, 100);

    @Override
    default AggregateEventTagger<GitEvent> aggregateTag() {
        return INSTANCE;
    }

    @JsonTypeName("artifact-registered")
    final record ArtifactRegistered(
        ArtifactCoordinates coordinates
    ) implements GitEvent {
        @JsonCreator
        public ArtifactRegistered {
        }
    }

    @JsonTypeName("repository-registered")
    final record RepoRegistered(
        ArtifactCoordinates coordinates,
        URI repository
    ) implements GitEvent {
        @JsonCreator
        public RepoRegistered {
        }
    }

    @JsonTypeName("commit-associated")
    final record CommitAssociatedWithVersion(
        String sha,
        URI repository,
        MavenCoordinates coordinates
    ) implements GitEvent {
        @JsonCreator
        public CommitAssociatedWithVersion {
        }
    }

    @JsonTypeName("version-associated")
    final record VersionRegistered(MavenCoordinates coordinates) implements GitEvent {
        @JsonCreator
        public VersionRegistered {
        }
    }

    @JsonTypeName("ArtifactsResolvedAsMissingCommit")
    final record ArtifactLabeledMissingCommit(MavenCoordinates coordinates)
        implements GitEvent {
        @JsonCreator
        public ArtifactLabeledMissingCommit {
        }
    }

    @JsonTypeName("commit-details-updated")
    final record CommitDetailsUpdated(MavenCoordinates coordinates, VersionedCommit commit) implements GitEvent {

        @JsonCreator
        public CommitDetailsUpdated {
        }
    }
}
