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
package org.spongepowered.downloads.versions.worker;

import akka.actor.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.Adapter;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import org.spongepowered.downloads.artifact.api.ArtifactService;
import org.spongepowered.downloads.auth.api.utils.AuthUtils;
import org.spongepowered.downloads.versions.api.VersionsService;
import play.Environment;
import play.libs.akka.AkkaGuiceSupport;

import javax.inject.Inject;

public class WorkerModule extends AbstractModule implements ServiceGuiceSupport, AkkaGuiceSupport {

    private final AuthUtils auth;

    @SuppressWarnings("unused") // These parameters must match for Play's Guice handling to work.
    @Inject
    public WorkerModule(final Environment environment, final com.typesafe.config.Config config) {
        this.auth = AuthUtils.configure(config);
    }

    @Override
    protected void configure() {
        this.bind(new TypeLiteral<ActorRef<Void>>() {
            })
            .toProvider(WorkerProvider.class)
            .asEagerSingleton();

    }

    public static record WorkerProvider(
        ArtifactService artifacts,
        VersionsService versions,
        ClusterSharding sharding,
        ActorSystem system
    ) implements Provider<ActorRef<Void>> {

        @Inject
        public WorkerProvider {
        }

        @Override
        public ActorRef<Void> get() {
            return Adapter.spawn(
                this.system,
                VersionsWorkerSupervisor.create(
                    this.artifacts,
                    this.versions
                ),
                "VersionsWorkerSupervisor"
            );
        }
    }
}
