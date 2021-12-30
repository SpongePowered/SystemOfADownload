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
package org.spongepowered.downloads.versions.worker.actor.artifacts;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.ServiceKey;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.typed.javadsl.ActorFlow;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.List;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;

import java.time.Duration;

public final class FileCollectionOperator {

    public static final ServiceKey<Request> KEY = ServiceKey.create(Request.class, "file-collection-operator");

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({
        @JsonSubTypes.Type(TryFindingCommitForFiles.class)
    })
    @JsonDeserialize
    public sealed interface Request extends Jsonable {}

    @JsonDeserialize
    public static final record TryFindingCommitForFiles(
        List<PotentiallyUsableAsset> files,
        MavenCoordinates coordinates
    ) implements Request {}

    public static Behavior<Request> scanJarFilesForCommit(
        final ActorRef<CommitExtractor.ChildCommand> commitExtractor,
        final ActorRef<CommitExtractor.AssetCommitResponse> receiverRef
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Request.class)
            .onMessage(TryFindingCommitForFiles.class, msg -> {
                if (ctx.getLog().isTraceEnabled()) {
                    ctx.getLog().trace("Received request for {}", msg);
                }
                final List<PotentiallyUsableAsset> files = msg.files();
                final var from = Source.from(files);
                final var extraction = ActorFlow.ask(
                    4,
                    commitExtractor,
                    Duration.ofMinutes(20),
                    CommitExtractor.AttemptFileCommit::new
                );
                final var receiverSink = Sink.foreach(receiverRef::tell);
                from.via(extraction).to(receiverSink).run(ctx.getSystem());
                return Behaviors.same();
            })
            .build());
    }
}
