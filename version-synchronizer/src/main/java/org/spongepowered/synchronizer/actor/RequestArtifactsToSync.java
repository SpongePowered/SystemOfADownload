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
package org.spongepowered.synchronizer.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.query.GetArtifactsResponse;

public class RequestArtifactsToSync {

    public interface Command {
    }

    public static final record GatherGroupArtifacts(
        String groupCoordinates,
        ActorRef<RequestArtifactsToSync.ArtifactsToSync> replyTo
    ) implements Command {
    }

    private static final record WrappedArtifactsToSync(
        ArtifactsToSync result,
        ActorRef<ArtifactsToSync> replyTo
    ) implements Command {
    }

    public static final record ArtifactsToSync(
        List<ArtifactCoordinates> artifactsNeeded
    ) {
    }

    public static Behavior<RequestArtifactsToSync.Command> create(
        final ArtifactService artifactService
    ) {
        return Behaviors.receive((ctx, msg) -> {
            if (msg instanceof GatherGroupArtifacts g) {
                final var listCompletableFuture = artifactService.getArtifacts(g.groupCoordinates).invoke()
                    .thenApply(artifactsResponse -> {
                        if (!(artifactsResponse instanceof GetArtifactsResponse.ArtifactsAvailable a)) {
                            return List.<ArtifactCoordinates>empty();
                        }
                        return a.artifactIds()
                            .map(id -> new ArtifactCoordinates(g.groupCoordinates, id));
                    });
                ctx.pipeToSelf(listCompletableFuture, (ok, exception) -> {
                    if (exception == null) {
                        return new WrappedArtifactsToSync(new ArtifactsToSync(ok), g.replyTo);
                    }
                    return new WrappedArtifactsToSync(new ArtifactsToSync(List.empty()), g.replyTo);
                });
                return Behaviors.same();
            }
            if (msg instanceof WrappedArtifactsToSync w) {
                w.replyTo.tell(w.result);
            }
            return Behaviors.same();
        });
    }

}
