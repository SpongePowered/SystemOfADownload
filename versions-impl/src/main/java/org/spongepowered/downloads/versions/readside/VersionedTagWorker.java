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
package org.spongepowered.downloads.versions.readside;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class VersionedTagWorker {

    public interface Command {
    }

    static final record RefreshVersionTags() implements Command {
    }

    static final record RefreshVersionRecommendation(ArtifactCoordinates coordinates) implements Command {
    }

    private static final record TimedOut() implements Command {
    }

    private static final record Completed(Data data, boolean updatedVersionedTags, int rowsAffected)
        implements Command {
    }

    private static final record Failed(Data data) implements Command {
    }

    private static final record Data(Optional<Instant> refreshVersions,
                                     Set<ArtifactCoordinates> refreshRecommendations) {

        Data requestedRefreshVersions() {
            return new Data(Optional.of(Instant.now()), this.refreshRecommendations);
        }

        Data updateArtifactRecommendation(ArtifactCoordinates coordinates) {
            return new Data(this.refreshVersions, this.refreshRecommendations.add(coordinates));
        }
    }

    public static Behavior<VersionedTagWorker.Command> create(
        final JpaSession session
    ) {
        return idle(session);
    }

    private static Behavior<Command> idle(final JpaSession session) {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
            .onMessage(
                RefreshVersionTags.class,
                cmd -> timed(new Data(Optional.of(Instant.now()), HashSet.empty()), session)
            )
            .onMessage(
                RefreshVersionRecommendation.class,
                cmd -> timed(new Data(Optional.empty(), HashSet.of(cmd.coordinates)), session)
            )
            .onMessage(Completed.class, cmd -> {
                ctx.getLog().info("Completed refresh of {}, affected {}", cmd.data, cmd.rowsAffected);
                return Behaviors.same();
            })
            .onMessage(Failed.class, cmd -> {
                ctx.getLog().warn("Recovering from failed update, will re-attempt");
                return timed(cmd.data, session);
            })
            .build()
        );
    }

    private static Behavior<Command> timed(
        final Data data, final JpaSession session
    ) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(timers -> {
            timers.startSingleTimer(new TimedOut(), Duration.ofSeconds(10));
            return waiting(data, session);
        }));
    }

    private static Behavior<Command> waiting(
        final Data data, final JpaSession session
    ) {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
            .onMessage(
                RefreshVersionTags.class,
                cmd -> waiting(data.requestedRefreshVersions(), session)
            )
            .onMessage(
                RefreshVersionRecommendation.class,
                cmd -> waiting(data.updateArtifactRecommendation(cmd.coordinates), session)
            )
            .onMessage(TimedOut.class, timeout -> {
                ctx.pipeToSelf(session.withTransaction(em -> {
                    final var updatedVersionedTags = data.refreshVersions
                        .map(time -> em
                            .createNativeQuery("select version.refreshversionedtags()")
                            .getSingleResult())
                        .isPresent();
                    final int rowsAffected = data.refreshRecommendations
                        .map(coordinates -> em.createNativeQuery(
                                "select version.refreshVersionRecommendations(:artifactId, :groupId)")
                            .setParameter("artifactId", coordinates.artifactId)
                            .setParameter("groupId", coordinates.groupId)
                            .getSingleResult())
                        .sum().intValue();
                    return new Completed(data, updatedVersionedTags, rowsAffected);
                }), (msg, throwable) -> {
                    if (throwable != null) {
                        ctx.getLog().error("Failed to handle updating artifacts, aborting", throwable);
                        return new Failed(data);
                    }
                    return msg;
                });
                return idle(session);
            })
            .build());
    }
}
