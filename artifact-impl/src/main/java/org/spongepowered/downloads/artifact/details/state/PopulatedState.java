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

@JsonDeserialize
public final class PopulatedState implements DetailsState, CompressedJsonable {

    public final ArtifactCoordinates coordinates;
    public final String displayName;
    public final String website;
    public final String gitRepository;
    public final String issues;

    @JsonCreator
    public PopulatedState(
        final ArtifactCoordinates coordinates, final String displayName, final String website,
        final String gitRepository,
        final String issues
    ) {
        this.coordinates = coordinates;
        this.displayName = displayName;
        this.website = website;
        this.gitRepository = gitRepository;
        this.issues = issues;
    }

    @Override
    public ArtifactCoordinates coordinates() {
        return this.coordinates;
    }

    @Override
    public String displayName() {
        return this.displayName;
    }

    @Override
    public String website() {
        return this.website;
    }

    @Override
    public String gitRepository() {
        return this.gitRepository;
    }

    @Override
    public String issues() {
        return this.issues;
    }

    public boolean isEmpty() {
        return this.coordinates.artifactId.isBlank() && this.coordinates.groupId.isBlank();
    }
}
