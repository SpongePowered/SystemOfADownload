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
package org.spongepowered.synchronizer;

import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.SingletonActor;
import akka.persistence.typed.PersistenceId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.versions.api.VersionsService;
import org.spongepowered.synchronizer.actor.CommitRegistrar;
import org.spongepowered.synchronizer.assetsync.VersionConsumer;
import org.spongepowered.synchronizer.gitmanaged.ArtifactSubscriber;
import org.spongepowered.synchronizer.gitmanaged.CommitConsumer;
import org.spongepowered.synchronizer.gitmanaged.ScheduledCommitResolver;
import org.spongepowered.synchronizer.gitmanaged.domain.GitManagedArtifact;
import org.spongepowered.synchronizer.resync.ResyncManager;
import org.spongepowered.synchronizer.resync.domain.ArtifactSynchronizerAggregate;
import org.spongepowered.synchronizer.versionsync.ArtifactConsumer;

public final class SonatypeSynchronizer {

    public interface Command {
    }

    public static Behavior<SonatypeSynchronizer.Command> create(
        final ArtifactService artifactService,
        final VersionsService versionsService,
        final ClusterSharding clusterSharding,
        final ObjectMapper mapper
    ) {
        return Behaviors.setup(context -> {
            // don't do this, eventually we can swap this out from service layers
            context.spawnAnonymous(Behaviors.supervise(CommitRegistrar.register(versionsService))
                .onFailure(SupervisorStrategy.restart()));
            CommitConsumer.setupSubscribers(versionsService, context);
            ArtifactSubscriber.setup(artifactService, context);
            ScheduledCommitResolver.setup(artifactService, context);

            final var settings = SynchronizationExtension.SettingsProvider.get(context.getSystem());
            clusterSharding
                .init(
                    Entity.of(
                        ArtifactSynchronizerAggregate.ENTITY_TYPE_KEY,
                        ArtifactSynchronizerAggregate::create
                    )
                );
            clusterSharding.init(Entity.of(GitManagedArtifact.ENTITY_TYPE_KEY, ctx -> GitManagedArtifact.create(PersistenceId.of(ctx.getEntityTypeKey().name(), ctx.getEntityId()), ctx.getEntityId())));

            ArtifactConsumer.subscribeToArtifactUpdates(
                context, artifactService, versionsService, clusterSharding, settings);

            VersionConsumer.subscribeToVersionedArtifactUpdates(versionsService, mapper, context, settings);

            final var resyncManager = ResyncManager.create(artifactService, settings.versionSync);
            final var resyncBehavior = Behaviors.supervise(resyncManager)
                .onFailure(
                SupervisorStrategy.restart());
            final var actor = SingletonActor.of(resyncBehavior, "artifact-sync");
            ClusterSingleton.get(context.getSystem()).init(actor);

            // Scheduled full resynchronization with maven and therefor sonatype
            return Behaviors.receive(SonatypeSynchronizer.Command.class)
                .build();
        });
    }

}
