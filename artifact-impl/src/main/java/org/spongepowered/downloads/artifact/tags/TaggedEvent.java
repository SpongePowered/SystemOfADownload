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
package org.spongepowered.downloads.artifact.tags;

import com.lightbend.lagom.serialization.Jsonable;

import java.io.Serial;
import java.util.Objects;

public interface TaggedEvent extends Jsonable {

    final static class CreatedTaggedVersion implements TaggedEvent {
        @Serial private static final long serialVersionUID = 0L;
        private final String mavenVersion;
        private final String tag;
        private final String tagValue;

        public CreatedTaggedVersion(final String mavenVersion, final String tag, final String tagValue) {
            this.mavenVersion = mavenVersion;
            this.tag = tag;
            this.tagValue = tagValue;
        }

        public String mavenVersion() {
            return this.mavenVersion;
        }

        public String tag() {
            return this.tag;
        }

        public String tagValue() {
            return this.tagValue;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (CreatedTaggedVersion) obj;
            return Objects.equals(this.mavenVersion, that.mavenVersion) &&
                Objects.equals(this.tag, that.tag) &&
                Objects.equals(this.tagValue, that.tagValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.mavenVersion, this.tag, this.tagValue);
        }

        @Override
        public String toString() {
            return "CreatedTaggedVersion[" +
                "mavenVersion=" + this.mavenVersion + ", " +
                "tag=" + this.tag + ", " +
                "tagValue=" + this.tagValue + ']';
        }
    }
}
