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
package org.spongepowered.downloads.maven.snapshot;

import io.vavr.collection.List;

/**
 * Represents a snapshot versioned maven metadata xml that SOAD will use to represent
 * possible artifacts of snapshots. Note that due to the implicit requirements of
 */
public class SnapshotVersioning {

    public final Snapshot snapshot;
    public final String lastUpdated;
    public final List<SnapshotAsset> snapshotVersions;

    public SnapshotVersioning(
        final Snapshot snapshot, final String lastUpdated,
        final List<SnapshotAsset> snapshotVersions
    ) {
        this.snapshot = snapshot;
        this.lastUpdated = lastUpdated;
        this.snapshotVersions = snapshotVersions;
    }
}
