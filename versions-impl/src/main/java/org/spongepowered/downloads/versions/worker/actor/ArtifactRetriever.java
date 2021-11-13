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
package org.spongepowered.downloads.versions.worker.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.versions.worker.actor.global.GlobalCommand;
import org.spongepowered.downloads.versions.worker.actor.global.GlobalRegistration;

import java.time.Duration;

public final class ArtifactRetriever {

    private static final record Response(List<ArtifactCoordinates> artifacts, ActorRef<List<ArtifactCoordinates>> replyTo)
    implements GlobalCommand {
        @JsonCreator
        private Response {
        }
    }

    public static Behavior<GlobalCommand> getArtifacts() {
        return Behaviors.setup(ctx -> {
            final var sharding = ClusterSharding.get(ctx.getSystem());
            return Behaviors.receive(GlobalCommand.class)
                .onMessage(GlobalCommand.GetArtifacts.class, msg -> {
                    ctx.pipeToSelf(sharding.entityRefFor(GlobalRegistration.ENTITY_TYPE_KEY, "global")
                        .<List<ArtifactCoordinates>>ask(
                            GlobalCommand.GetArtifacts::new,
                            Duration.ofSeconds(10))
                        .toCompletableFuture(),
                        (result, throwable) -> {
                        if (throwable != null) {
                            ctx.getLog().warn("Failed to retrieve global artifacts", throwable);
                            return new Response(List.empty(), msg.replyTo());
                        }
                        return new Response(result, msg.replyTo());
                        });
                    return Behaviors.same();
                })
                .onMessage(Response.class, msg -> {
                    msg.replyTo.tell(msg.artifacts);
                    return Behaviors.same();
                })
                .build();
        });
    }
}
