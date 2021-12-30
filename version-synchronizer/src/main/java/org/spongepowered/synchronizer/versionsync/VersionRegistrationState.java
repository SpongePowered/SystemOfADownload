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
package org.spongepowered.synchronizer.versionsync;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.api.MavenCoordinates;

@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = VersionRegistrationState.Empty.class,
        name = "empty"),
    @JsonSubTypes.Type(value = VersionRegistrationState.Registered.class,
        name = "registered")
})
public sealed interface VersionRegistrationState extends Jsonable {

    ArtifactCoordinates coordinates();

    default boolean isActive() {
        return false;
    }

    VersionRegistrationState acceptBatch(
        final ArtifactCoordinates artifact, final List<MavenCoordinates> coordinates
    );

    VersionRegistrationState acceptVersion(MavenCoordinates coordinates);

    default boolean hasVersion(MavenCoordinates coordinates) {
        return false;
    }

    default List<MavenCoordinates> getNextBatch() {
        return List.empty();
    }

    VersionRegistrationState resolvedVersion(MavenCoordinates coordinates);

    default List<MavenCoordinates> getPending() {
        return List.empty();
    }

    VersionRegistrationState startBatch(List<MavenCoordinates> batched);

    VersionRegistrationState failedVersion(MavenCoordinates coordinates);

    @JsonDeserialize
    record Empty() implements VersionRegistrationState {
        @JsonCreator
        public Empty {
        }

        @Override
        public ArtifactCoordinates coordinates() {
            return new ArtifactCoordinates("", "");
        }

        @Override
        public VersionRegistrationState acceptBatch(
            final ArtifactCoordinates artifact,
            final List<MavenCoordinates> coordinates
        ) {
            return new Registered(artifact, coordinates.toMap(c -> c.version, (c) -> Registration.UNREGISTERED));
        }

        @Override
        public VersionRegistrationState acceptVersion(final MavenCoordinates coordinates) {
            return new Registered(
                coordinates.asArtifactCoordinates(),
                HashMap.of(coordinates.version, Registration.UNREGISTERED)
            );
        }

        @Override
        public VersionRegistrationState resolvedVersion(final MavenCoordinates coordinates) {
            return new Registered(
                coordinates.asArtifactCoordinates(),
                HashMap.of(coordinates.version, Registration.REGISTERED)
            );
        }

        @Override
        public VersionRegistrationState startBatch(
            final List<MavenCoordinates> batched
        ) {
            return new Active(
                batched.head().asArtifactCoordinates(),
                batched.toMap(c -> c.version, (c) -> Registration.UNREGISTERED),
                batched
            );
        }

        @Override
        public VersionRegistrationState failedVersion(final MavenCoordinates coordinates) {
            return new Registered(coordinates.asArtifactCoordinates(), HashMap.of(coordinates.version, Registration.UNREGISTERED));
        }
    }

    @JsonDeserialize
    enum Registration {
        REGISTERED,
        UNREGISTERED;
    }

    @JsonDeserialize
    record Registered(
        ArtifactCoordinates coordinates,
        Map<String, Registration> versions
    ) implements VersionRegistrationState {
        @JsonCreator
        public Registered {
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean hasVersion(final MavenCoordinates coordinates) {
            return this.versions.containsKey(coordinates.version);
        }

        @Override
        public VersionRegistrationState acceptBatch(
            final ArtifactCoordinates artifact,
            final List<MavenCoordinates> coordinates
        ) {
            return new Registered(
                this.coordinates,
                this.versions.merge(
                    coordinates.toMap(c -> c.version, (c) -> Registration.UNREGISTERED),
                    (existing, registration) -> existing
                )
            );
        }

        @Override
        public VersionRegistrationState acceptVersion(final MavenCoordinates coordinates) {
            if (this.versions.containsKey(coordinates.version)) {
                return this;
            }
            return new Registered(
                this.coordinates,
                this.versions.put(coordinates.version, Registration.UNREGISTERED)
            );
        }

        @Override
        public List<MavenCoordinates> getNextBatch() {
            return this.versions.filterValues(registration -> registration == Registration.UNREGISTERED)
                .keySet()
                .map(this.coordinates::version)
                .toList()
                .sorted()
                .take(10);
        }

        @Override
        public VersionRegistrationState resolvedVersion(final MavenCoordinates coordinates) {
            return new Registered(
                this.coordinates,
                this.versions.put(coordinates.version, Registration.REGISTERED)
            );
        }

        @Override
        public VersionRegistrationState startBatch(
            final List<MavenCoordinates> batched
        ) {
            final var toBatch = batched.filter(
                c -> this.versions.getOrElse(c.version, Registration.UNREGISTERED) == Registration.UNREGISTERED);
            return new Active(
                this.coordinates,
                this.versions.merge(
                    toBatch.toMap(c -> c.version, (c) -> Registration.UNREGISTERED),
                    (existing, registration) -> existing
                ),
                toBatch
            );
        }

        @Override
        public VersionRegistrationState failedVersion(final MavenCoordinates coordinates) {
            if (this.versions.containsKey(coordinates.version)) {
                return this;
            }
            return new Registered(
                this.coordinates,
                this.versions.put(coordinates.version, Registration.UNREGISTERED)
            );
        }
    }

    record Active(
        ArtifactCoordinates coordinates,
        Map<String, Registration> versions,
        List<MavenCoordinates> queue
    ) implements VersionRegistrationState {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public boolean hasVersion(final MavenCoordinates coordinates) {
            return this.versions.containsKey(coordinates.version);
        }

        @Override
        public VersionRegistrationState resolvedVersion(final MavenCoordinates coordinates) {
            final var pending = this.queue.remove(coordinates);
            final var updated = this.versions.put(coordinates.version, Registration.REGISTERED);
            if (pending.isEmpty()) {
                return new Registered(
                    this.coordinates,
                    updated
                );
            }
            return new Active(
                this.coordinates,
                updated,
                pending
            );
        }

        @Override
        public VersionRegistrationState acceptBatch(
            final ArtifactCoordinates artifact,
            final List<MavenCoordinates> coordinates
        ) {
            return new Active(
                this.coordinates,
                this.versions.merge(
                    coordinates.toMap(c -> c.version, (c) -> Registration.UNREGISTERED),
                    (existing, registration) -> existing
                ),
                this.queue
            );
        }

        @Override
        public VersionRegistrationState acceptVersion(final MavenCoordinates coordinates) {
            if (this.versions.containsKey(coordinates.version)) {
                return this;
            }
            return new Active(
                this.coordinates,
                this.versions.put(coordinates.version, Registration.UNREGISTERED),
                this.queue
            );
        }

        @Override
        public List<MavenCoordinates> getPending() {
            return this.queue;
        }

        @Override
        public VersionRegistrationState startBatch(
            final List<MavenCoordinates> batched
        ) {
            if (!this.queue.isEmpty()) {
                return this;
            }
            final var toBatch = batched.filter(
                c -> this.versions.getOrElse(c.version, Registration.UNREGISTERED) == Registration.UNREGISTERED
            ).appendAll(this.queue);
            return new Active(
                this.coordinates,
                this.versions.merge(
                    toBatch.toMap(c -> c.version, (c) -> Registration.UNREGISTERED),
                    (existing, registration) -> existing
                ),
                toBatch
            );
        }

        @Override
        public VersionRegistrationState failedVersion(final MavenCoordinates coordinates) {
            if (this.versions.containsKey(coordinates.version)) {
                return this;
            }
            return new Active(
                this.coordinates,
                this.versions.put(coordinates.version, Registration.UNREGISTERED),
                this.queue
            );
        }

        @Override
        public List<MavenCoordinates> getNextBatch() {
            if (this.queue.isEmpty()) {
                return this.versions.filterValues(registration -> registration == Registration.UNREGISTERED)
                    .keySet()
                    .map(this.coordinates::version)
                    .toList()
                    .sorted()
                    .take(10);
            }
            return this.queue;
        }
    }
}
