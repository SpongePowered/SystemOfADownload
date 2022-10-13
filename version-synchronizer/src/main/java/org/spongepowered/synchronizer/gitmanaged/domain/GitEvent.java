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
package org.spongepowered.synchronizer.gitmanaged.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;

@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GitEvent.RepositoryRegistered.class),
    @JsonSubTypes.Type(value = GitEvent.CommitRegistered.class),
    @JsonSubTypes.Type(value = GitEvent.CommitResolved.class),
})
public sealed interface GitEvent extends AggregateEvent<GitEvent>, Jsonable {

    AggregateEventShards<GitEvent> INSTANCE = AggregateEventTag.sharded(GitEvent.class, 3);

    @Override
    default AggregateEventTagger<GitEvent> aggregateTag() {
        return INSTANCE;
    }

    @JsonTypeName("repository-registered")
    record RepositoryRegistered(URI repository) implements GitEvent {
        @JsonCreator
        public RepositoryRegistered {
        }
    }
    @JsonTypeName("commit-extracted")
    record CommitRegistered(MavenCoordinates coordinates, String commit) implements GitEvent {
        @JsonCreator
        public CommitRegistered {
        }
    }
    @JsonTypeName("commit-resolved")
    record CommitResolved(MavenCoordinates coordinates, VersionedCommit resolvedCommit) implements GitEvent {
        @JsonCreator
        public CommitResolved {
        }
    }

    @JsonTypeName("commit-unresolved")
    record CommitUnresolvable(MavenCoordinates coordinates, String commit) implements GitEvent {
        @JsonCreator
        public CommitUnresolvable {
        }
    }
}
