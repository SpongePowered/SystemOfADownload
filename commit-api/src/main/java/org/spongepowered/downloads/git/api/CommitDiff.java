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
package org.spongepowered.downloads.git.api;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

@Immutable
public final class CommitDiff {
    private final CommitSha fromSha;
    private final CommitSha toSha;

    public CommitDiff(
        final CommitSha fromSha,
        final CommitSha toSha
    ) {
        this.fromSha = fromSha;
        this.toSha = toSha;
    }

    public CommitSha fromSha() {
        return this.fromSha;
    }

    public CommitSha toSha() {
        return this.toSha;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final var that = (CommitDiff) obj;
        return Objects.equals(this.fromSha, that.fromSha) &&
            Objects.equals(this.toSha, that.toSha);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.fromSha, this.toSha);
    }

    @Override
    public String toString() {
        return "CommitDiff[" +
            "fromSha=" + this.fromSha + ", " +
            "toSha=" + this.toSha + ']';
    }

}
