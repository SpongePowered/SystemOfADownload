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

import akka.Done;
import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;
import org.spongepowered.downloads.versions.api.models.VersionedCommit;

import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonDeserialize
@JsonSubTypes({
    @JsonSubTypes.Type(value = GitCommand.RegisterRepository.class, name = "register-repository"),
    @JsonSubTypes.Type(value = GitCommand.RegisterRawCommit.class, name = "register-raw-commit"),
    @JsonSubTypes.Type(value = GitCommand.GetRepositories.class, name = "get-repositories"),
    @JsonSubTypes.Type(value = GitCommand.GetUnresolvedVersions.class, name = "get-unresolved-versions"),
    @JsonSubTypes.Type(value = GitCommand.MarkVersionAsResolved.class, name = "mark-version-as-resolved"),
    @JsonSubTypes.Type(value = GitCommand.MarkVersionAsUnresolveable.class, name = "mark-version-as-unresolveable"),
})
public sealed interface GitCommand extends Jsonable {

    record RegisterRepository(URI repository, ActorRef<Done> replyTo) implements GitCommand {
        @JsonCreator
        public RegisterRepository {
        }
    }

    record RegisterRawCommit(
        MavenCoordinates coordinates,
        String commitSha,
        ActorRef<Done> replyTo
    ) implements GitCommand {
        @JsonCreator
        public RegisterRawCommit {
        }
    }

    @JsonDeserialize
    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    sealed interface RepositoryResponse extends Jsonable {
        List<URI> repositories();
    }

    @JsonDeserialize
    record RepositoriesAvaiable(List<URI> repositories) implements RepositoryResponse {
        @JsonCreator
        public RepositoriesAvaiable {
        }
    }

    @JsonDeserialize
    record NoRepositories() implements RepositoryResponse {
        @JsonCreator
        public NoRepositories {
        }

        @Override
        public List<URI> repositories() {
            return List.empty();
        }
    }

    record GetRepositories(ActorRef<RepositoryResponse> replyTo) implements GitCommand {
        @JsonCreator
        public GetRepositories {
        }
    }

    record GetUnresolvedVersions(ActorRef<UnresolvedWork> replyTo) implements GitCommand {
        @JsonCreator
        public GetUnresolvedVersions {
        }
    }

    record MarkVersionAsResolved(
        MavenCoordinates coordinates,
        VersionedCommit commit,
        ActorRef<Done> replyTo
    ) implements GitCommand {
        @JsonCreator
        public MarkVersionAsResolved {
        }
    }

    record MarkVersionAsUnresolveable(
        ActorRef<Done> replyTo,
        MavenCoordinates coordinates,
        String commitSha
    ) implements GitCommand {}


    static final UnresolvedWork EMPTY = new UnresolvedWork(HashMap.empty(), List.empty());
    @JsonDeserialize
    @JsonSerialize
    record UnresolvedWork(
        Map<MavenCoordinates, String> unresolvedCommits,
        List<URI> repositories
    ) implements Jsonable {
        @JsonCreator
        public UnresolvedWork {
        }

        public boolean isEmpty() {
            return unresolvedCommits.isEmpty();
        }

        public boolean hasWork() {
            return !unresolvedCommits.isEmpty() && !this.repositories.isEmpty();
        }
    }
}
