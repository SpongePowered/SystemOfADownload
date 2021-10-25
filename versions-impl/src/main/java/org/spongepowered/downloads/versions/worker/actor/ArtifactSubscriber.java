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

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.japi.Pair;
import akka.stream.typed.javadsl.ActorFlow;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.artifact.api.event.ArtifactUpdate;
import org.spongepowered.downloads.versions.worker.actor.global.GlobalCommand;
import org.spongepowered.downloads.versions.worker.actor.global.GlobalRegistration;
import org.spongepowered.downloads.versions.worker.akka.FlowUtil;
import org.spongepowered.downloads.versions.worker.domain.GitBasedArtifact;
import org.spongepowered.downloads.versions.worker.domain.GitCommand;

import java.time.Duration;

public final class ArtifactSubscriber {

    public interface Command {
    }

    public static Behavior<Command> consumeMessages(
        ArtifactService artifacts
    ) {
        return Behaviors.setup(ctx -> {
            final var self = ctx.getSelf();
            final var sharding = ClusterSharding.get(ctx.getSystem());

            artifacts.artifactUpdate()
                .subscribe()
                .atLeastOnce(FlowUtil.splitClassFlows(
                    Pair.create(
                        ArtifactUpdate.GitRepositoryAssociated.class,
                        FlowUtil.subClassFlow(ActorFlow.ask(
                            16,
                            self,
                            Duration.ofMinutes(20),
                            GitRepositoryAssociatedFanout::new
                        ))
                    ), Pair.create(
                        ArtifactUpdate.ArtifactRegistered.class,
                        FlowUtil.subClassFlow(ActorFlow.ask(
                            16,
                            self,
                            Duration.ofMinutes(20),
                            ReceiveArtifactRegistration::new
                        ))
                    )));
            return Behaviors.receive(Command.class)
                .onMessage(ReceiveArtifactRegistration.class, msg -> forwardArtifactRegistered(ctx, sharding, msg))
                .onMessage(RegistrationSucceeded.class, msg -> {
                    msg.replyTo.tell(Done.done());
                    return Behaviors.same();
                })
                .onMessage(GitRepositoryAssociatedFanout.class, msg -> fanOutGitRepositoryUpdates(ctx, sharding, msg))
                .onMessage(FailedGitRegistration.class, msg -> {
                    msg.replyTo.tell(Done.done());
                    return Behaviors.same();
                })
                .build();
        });
    }

    private static record ReceiveArtifactRegistration(
        ArtifactUpdate.ArtifactRegistered registered,
        ActorRef<Done> replyTo
    ) implements Command {
    }

    private static record FailedArtifactRegistration(
        ArtifactUpdate.ArtifactRegistered registered,
        ActorRef<Done> replyTo
    ) implements Command {

    }

    private static record RegistrationSucceeded(
        ActorRef<Done> replyTo
    ) implements Command {

    }

    private static record GitRepositoryAssociatedFanout(
        ArtifactUpdate.GitRepositoryAssociated registered,
        ActorRef<Done> replyTo
    ) implements Command {
    }

    private static record FailedGitRegistration(
        ArtifactUpdate.GitRepositoryAssociated registered,
        ActorRef<Done> replyTo
    ) implements Command {
    }


    private static Behavior<Command> forwardArtifactRegistered(
        final ActorContext<Command> ctx,
        final ClusterSharding sharding,
        final ReceiveArtifactRegistration registration
    ) {
        final ArtifactUpdate.ArtifactRegistered registered = registration.registered;
        ctx.pipeToSelf(
            sharding.entityRefFor(GitBasedArtifact.ENTITY_TYPE_KEY, registered.coordinates().asMavenString())
                .<Done>ask(
                    replyTo -> new GitCommand.RegisterArtifact(registered.coordinates(), replyTo),
                    Duration.ofMinutes(10)
                )
                .thenCombineAsync(
                    sharding.entityRefFor(GlobalRegistration.ENTITY_TYPE_KEY, "global")
                        .<Done>ask(
                            replyTo -> new GlobalCommand.RegisterArtifact(replyTo, registered.coordinates()),
                            Duration.ofSeconds(10)
                        ),
                    (done1, done2) -> Done.done()
                )
                .toCompletableFuture(),
            (done, throwable) -> {
                if (throwable != null) {
                    registration.replyTo.tell(Done.done());
                    return new FailedArtifactRegistration(registered, registration.replyTo);
                }
                return new RegistrationSucceeded(registration.replyTo);
            }
        );
        return Behaviors.same();
    }

    private static Behavior<Command> fanOutGitRepositoryUpdates(
        final ActorContext<Command> ctx,
        final ClusterSharding sharding,
        final GitRepositoryAssociatedFanout msg
    ) {
        final ArtifactUpdate.GitRepositoryAssociated repo = msg.registered;
        ctx.pipeToSelf(
            sharding.entityRefFor(
                    GitBasedArtifact.ENTITY_TYPE_KEY, repo.coordinates().asMavenString())
                .<Done>ask(
                    replyTo -> new GitCommand.RegisterRepository(repo.repository(), replyTo),
                    Duration.ofMinutes(1)
                ),
            (done, throwable) -> {
                if (throwable != null) {
                    return new FailedGitRegistration(repo, msg.replyTo);
                }
                return new RegistrationSucceeded(msg.replyTo);
            }
        );
        return Behaviors.same();
    }
}
