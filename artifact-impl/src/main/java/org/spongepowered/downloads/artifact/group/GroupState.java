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
package org.spongepowered.downloads.artifact.group;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import org.spongepowered.downloads.artifact.api.Group;
import org.spongepowered.downloads.utils.UUIDType5;

import java.util.UUID;

public final class GroupState {
    public final String groupCoordinates;
    public final String name;
    public final String website;
    public final Set<String> artifacts;
    public final UUID groupId;

    static GroupState empty() {
        return new GroupState("", "", "https://example.com", HashSet.empty());
    }

    GroupState(
        final String groupCoordinates, final String name, final String website, final Set<String> artifacts
    ) {
        this.groupCoordinates = groupCoordinates;
        this.name = name;
        this.website = website;
        this.groupId = UUIDType5.nameUUIDFromNamespaceAndString(UUIDType5.NAMESPACE_OID, this.groupCoordinates);
        this.artifacts = artifacts;
    }

    public boolean isEmpty() {
        return this.groupCoordinates.isEmpty() || this.name.isEmpty();
    }

    public Group asGroup() {
        return new Group(this.groupCoordinates, this.name, this.website);
    }
}
