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
package org.spongepowered.downloads.artifact.details.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.CompressedJsonable;
import org.spongepowered.downloads.artifact.api.ArtifactCoordinates;
import org.spongepowered.downloads.artifact.details.DetailsEvent;

@JsonDeserialize
public record PopulatedState(ArtifactCoordinates coordinates,
                             String displayName, String website, String gitRepository,
                             String issues) implements DetailsState, CompressedJsonable {

    @JsonCreator
    public PopulatedState {
    }

    public boolean isEmpty() {
        return this.coordinates.artifactId().isBlank() && this.coordinates.groupId().isBlank();
    }

    public DetailsState withDisplayName(DetailsEvent.ArtifactDetailsUpdated event) {
        return new PopulatedState(
            this.coordinates,
            event.displayName(),
            this.website,
            this.gitRepository,
            this.issues
        );
    }

    public DetailsState withGitRepo(DetailsEvent.ArtifactGitRepositoryUpdated e) {
        return new PopulatedState(
            this.coordinates,
            this.displayName,
            this.website,
            e.gitRepo(),
            this.issues
        );
    }

    public DetailsState withIssues(DetailsEvent.ArtifactIssuesUpdated e) {
        return new PopulatedState(
            this.coordinates,
            this.displayName,
            this.website,
            this.gitRepository,
            e.url()
        );
    }

    public DetailsState withWebsite(DetailsEvent.ArtifactWebsiteUpdated e) {
        return new PopulatedState(
            this.coordinates,
            this.displayName,
            e.url(),
            this.gitRepository,
            this.issues
        );
    }
}
