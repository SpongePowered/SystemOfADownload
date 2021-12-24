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

import akka.Done;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.downloads.versions.api.models.CommitRegistration;

public class CommitRegistrar {

    public static Behavior<CommitDetailsRegistrar.Command> register(
        VersionsService versionsService
    ) {
        return Behaviors.setup(ctx -> {
            final var registration = Receptionist.register(CommitDetailsRegistrar.SERVICE_KEY, ctx.getSelf());
            ctx.getSystem().receptionist().tell(registration);
            final var auth = AuthUtils.configure(ctx.getSystem().settings().config());
            return Behaviors.receive(CommitDetailsRegistrar.Command.class)
                .onMessage(CommitDetailsRegistrar.HandleVersionedCommitReport.class, msg -> {
                    final var future = auth.internalAuth(versionsService.registerCommit(
                        msg.coordinates().groupId, msg.coordinates().artifactId, msg.coordinates().version
                    )).invoke(new CommitRegistration.ResolvedCommit(
                        msg.repo(),
                        msg.versionedCommit(),
                        msg.coordinates()
                    )).toCompletableFuture();
                    ctx.pipeToSelf(future, (done, failure) -> {
                        if (failure != null) {
                            ctx.getLog().warn("Failed registering git details", failure);
                        }
                        return new CommitDetailsRegistrar.CompletedWork(msg.replyTo());
                    });
                    return Behaviors.same();
                })
                .onMessage(CommitDetailsRegistrar.CommitNotFound.class, msg -> {
                    final var future = auth.internalAuth(versionsService.registerCommit(
                            msg.coordinates().groupId, msg.coordinates().artifactId, msg.coordinates().version
                        )).invoke(new CommitRegistration.FailedCommit(msg.commitId(), msg.repo()))
                        .toCompletableFuture();

                    ctx.pipeToSelf(future, (done, failure) -> {
                        if (failure != null) {
                            ctx.getLog().warn("Failed registering git details", failure);
                        }
                        return new CommitDetailsRegistrar.CompletedWork(msg.replyTo());
                    });
                    return Behaviors.same();
                })
                .onMessage(CommitDetailsRegistrar.CompletedWork.class, msg -> {
                    msg.replyTo().tell(Done.getInstance());
                    return Behaviors.same();
                })
                .build();
        });

    }

}
