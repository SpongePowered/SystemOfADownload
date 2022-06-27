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
package org.spongepowered.synchronizer.resync.domain;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandlerWithReply;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies;
import akka.persistence.typed.javadsl.ReplyEffect;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.vavr.collection.List;
import io.vavr.control.Try;
import io.vavr.jackson.datatype.VavrModule;
import org.spongepowered.downloads.maven.artifact.ArtifactMavenMetadata;
import org.spongepowered.synchronizer.resync.ResyncExtension;
import org.spongepowered.synchronizer.resync.ResyncSettings;

import javax.xml.stream.XMLInputFactory;
import java.io.Serial;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

public final class ArtifactSynchronizerAggregate
    extends EventSourcedBehaviorWithEnforcedReplies<Command, SynchronizeEvent, SyncState> {
    public static EntityTypeKey<Command> ENTITY_TYPE_KEY = EntityTypeKey.create(Command.class, "ArtifactSynchronizer");
    private final ActorContext<Command> ctx;
    private final ResyncSettings settings;
    private final HttpClient httpClient;
    private final XmlMapper mapper;

    public ArtifactSynchronizerAggregate(
        final EntityContext<Command> context, final ActorContext<Command> ctx,
        final ResyncSettings settings
    ) {
        super(
            // PersistenceId needs a typeHint (or namespace) and entityId,
            // we take then from the EntityContext
            PersistenceId.of(
                context.getEntityTypeKey().name(), // <- type hint
                context.getEntityId() // <- business id
            ));
        this.ctx = ctx;
        this.settings = settings;
        final var mapper = new XmlMapper();
        mapper.registerModule(new VavrModule());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(this.settings.timeout)
            .version(HttpClient.Version.HTTP_2)
            .build();
        this.mapper = mapper;
    }

    public static Behavior<Command> create(EntityContext<Command> context) {
        return Behaviors.setup(ctx -> {
            final ResyncSettings settings = ResyncExtension.SettingsProvider.get(ctx.getSystem());

            return new ArtifactSynchronizerAggregate(context, ctx, settings);
        });
    }

    @Override
    public SyncState emptyState() {
        return SyncState.EMPTY;
    }

    @Override
    public EventHandler<SyncState, SynchronizeEvent> eventHandler() {
        final var builder = newEventHandlerBuilder()
            .forAnyState()
            .onEvent(
                SynchronizeEvent.SynchronizedArtifacts.class,
                (event) -> new SyncState(event.updatedTime(), event.metadata())
            );
        return builder.build();
    }

    @Override
    public CommandHandlerWithReply<Command, SynchronizeEvent, SyncState> commandHandler() {
        final var builder = this.newCommandHandlerWithReplyBuilder()
            .forAnyState()
            .onCommand(Command.Resync.class, this::handleResync)
            .onCommand(Command.WrappedResync.class, this::handleResponse);
        return builder.build();
    }

    private ReplyEffect<SynchronizeEvent, SyncState> handleResponse(SyncState state, Command.WrappedResync cmd) {
        if (cmd.response() instanceof Command.Failed) {
            return this.Effect().reply(cmd.replyTo(), List.empty());
        }
        if (cmd.response() instanceof Command.Completed c) {
            final var metadata = c.metadata();
            if (metadata.versioning().lastUpdated.equals(state.lastUpdated)) {
                final var versionsToSync = state.versions.versioning()
                    .versions.map(state.coordinates()::version);
                return this.Effect()
                    .reply(cmd.replyTo(), versionsToSync);
            }
            return this.Effect()
                .persist(new SynchronizeEvent.SynchronizedArtifacts(metadata, metadata.versioning().lastUpdated))
                .thenReply(cmd.replyTo(), s -> s.versions.versioning().versions.map(s.coordinates()::version));
        }
        return this.Effect().reply(cmd.replyTo(), List.empty());
    }


    private ReplyEffect<SynchronizeEvent, SyncState> handleResync(SyncState state, Command.Resync cmd) {
        final var groupId = !state.groupId.equals(cmd.coordinates().groupId())
            ? cmd.coordinates().groupId()
            : state.groupId;
        final var artifactId = !state.artifactId.equals(cmd.coordinates().artifactId())
            ? cmd.coordinates().artifactId()
            : state.artifactId;
        ctx.pipeToSelf(
            getArtifactMetadata(groupId, artifactId),
            (response, throwable) -> {
                if (throwable != null) {
                    if (throwable instanceof UnsupportedArtifactException ua) {
                        this.ctx.getLog().warn(
                            String.format("Unsupported artifact %s", state.coordinates()),
                            throwable
                        );
                    } else {
                        this.ctx.getLog().error(
                            String.format("Unable to get maven-metadata.xml for artifact: %s", state.coordinates()),
                            throwable
                        );
                    }
                    return new Command.WrappedResync(new Command.Failed(), cmd.replyTo());
                }
                return new Command.WrappedResync(new Command.Completed(response), cmd.replyTo());
            }
        );
        return this.Effect().noReply();
    }

    private static final class UnsupportedArtifactException extends Exception {


        @Serial private static final long serialVersionUID = 4579607644429804821L;

        public UnsupportedArtifactException(final String artifact, final String url) {
            super(String.format("Unsupported artifact by id %s using url: %s", artifact, url));
        }
    }

    private CompletableFuture<ArtifactMavenMetadata> getArtifactMetadata(String groupId, String artifactId) {
        final var url = new StringJoiner("/", this.settings.repository, "")
            .add(groupId.replace(".", "/"))
            .add(artifactId)
            .add("maven-metadata.xml")
            .toString();
        return Try.of(() -> URI.create(url))
            .map(uri -> HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", this.settings.agentName)
                .build())
            .toCompletableFuture()
            .thenCompose(request -> this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenCompose(response -> Try.of(() -> response)
                    .filterTry(
                        r -> r.statusCode() == 200,
                        () -> new UnsupportedArtifactException(groupId + ":" + artifactId, url)
                    )
                    .toCompletableFuture()
                )
                .thenApply(HttpResponse::body)
                .thenApply(is -> Try.of(() -> XMLInputFactory.newFactory().createXMLStreamReader(is)).get())
                .thenCompose(reader -> Try.of(
                    () -> this.mapper.readValue(reader, ArtifactMavenMetadata.class)).toCompletableFuture())
            );
    }

}
