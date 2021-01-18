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
import java.util.StringJoiner;

/**
 * A Git commit is 160-bit id, which means that it can be composed of
 * two {@code long}s and an {@code int}. In totality, a commit can always
 * have the same sha as another commit for a totally different repository,
 * however, it's impossible for git to generate the same commit sha within
 * the same commit history (since a SHA-1 collision on a diff will be
 * rendered as the "same commit").
 * <p>To borrow an excerpt from the <a href="https://git-scm.com/book/en/v2/Git-Tools-Revision-Selection">Git book</a>:
 * <blockquote>
 * Here’s an example to give you an idea of what it would take to get a
 * SHA-1 collision. If all 6.5 billion humans on Earth were programming,
 * and every second, each one was producing code that was the equivalent
 * of the entire Linux kernel history (3.6 million Git objects) and pushing
 * it into one enormous Git repository, it would take roughly 2 years until
 * that repository contained enough objects to have a 50% probability of
 * a single SHA-1 object collision.
 * </blockquote>
 */
@Immutable
public final class CommitSha {

    public final long shaPart1, shaPart2;
    public final int shaPart3;

    public CommitSha(final long shaPart1, final long shaPart2, final int shaPart3) {
        this.shaPart1 = shaPart1;
        this.shaPart2 = shaPart2;
        this.shaPart3 = shaPart3;
    }

    public String toHexString() {
        return Long.toHexString(this.shaPart1)
            + Long.toHexString(this.shaPart2)
            + Integer.toHexString(this.shaPart3);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final CommitSha commitSha = (CommitSha) o;
        return this.shaPart1 == commitSha.shaPart1 &&
            this.shaPart2 == commitSha.shaPart2 &&
            this.shaPart3 == commitSha.shaPart3;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.shaPart1, this.shaPart2, this.shaPart3);
    }

    @Override
    public String toString() {
        return new StringJoiner(
            ", ", CommitSha.class.getSimpleName() + "[", "]")
            .add("sha=" + this.toHexString())
            .toString();
    }
}
