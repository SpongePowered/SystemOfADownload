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
package org.spongepowered.downloads.changelog;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import io.vavr.collection.List;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.spongepowered.downloads.changelog.event.ChangelogEvent;

import java.util.Optional;

@SuppressWarnings("unchecked")
public class ChangelogEntity extends PersistentEntity<ChangelogCommand, ChangelogEvent, ChangelogState> {

    private static final Logger LOGGER = LogManager.getLogger(ChangelogEntity.class);

    @Override
    public Behavior initialBehavior(final Optional<ChangelogState> snapshotState) {
        final BehaviorBuilder builder = this.newBehaviorBuilder(snapshotState.orElseGet(ChangelogState::empty));

        // Registering Artifacts (because the read side persistence for Artifact registration will read in
        // new Artifacts, but we effectively will have workers that do some saga work
        // like pulling the jar, associating the commit sha with the artifact, etc.
        builder.setCommandHandler(ChangelogCommand.RegisterArtifact.class, this::processRegisterArtifact);
        builder.setEventHandler(ChangelogEvent.ArtifactRegistered.class, this::handleRegisterArtifact);

        return builder.build();
    }

    private Persist<ChangelogEvent> processRegisterArtifact(
        final ChangelogCommand.RegisterArtifact cmd,
        final CommandContext<NotUsed> ctx
    ) {
        if (this.state().getCoordinates().isEmpty()) {
            ctx.thenPersist(new ChangelogEvent.ArtifactRegistered(cmd.artifact()));
        }
        return ctx.done();
    }

    private ChangelogState handleRegisterArtifact(final ChangelogEvent.ArtifactRegistered event) {
        return new ChangelogState(event.artifact().getFormattedString(":"), List.empty());
    }

}
