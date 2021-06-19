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

import java.util.Objects;

public final class SnapshotAsset {
    private final String classifier;
    private final String extension;
    private final String value;
    private final String updated;

    SnapshotAsset(final String classifier, final String extension, final String value, final String updated) {
        this.classifier = classifier;
        this.extension = extension;
        this.value = value;
        this.updated = updated;
    }

    public String classifier() {
        return this.classifier;
    }

    public String extension() {
        return this.extension;
    }

    public String value() {
        return this.value;
    }

    public String updated() {
        return this.updated;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final var that = (SnapshotAsset) obj;
        return Objects.equals(this.classifier, that.classifier) &&
            Objects.equals(this.extension, that.extension) &&
            Objects.equals(this.value, that.value) &&
            Objects.equals(this.updated, that.updated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.classifier, this.extension, this.value, this.updated);
    }

    @Override
    public String toString() {
        return "SnapshotAsset[" +
            "classifier=" + this.classifier + ", " +
            "extension=" + this.extension + ", " +
            "value=" + this.value + ", " +
            "updated=" + this.updated + ']';
    }
}
