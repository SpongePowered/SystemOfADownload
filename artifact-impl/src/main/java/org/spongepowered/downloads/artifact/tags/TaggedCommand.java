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

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import org.spongepowered.downloads.artifact.api.query.GetTaggedArtifacts;

import java.util.Objects;

public interface TaggedCommand {
    final class RequestTaggedVersions implements TaggedCommand {
        public final int limit;
        public final int offset;
        public final ActorRef<GetTaggedArtifacts.Response> replyTo;

        public RequestTaggedVersions(
            final int limit,
            final int offset,
            final ActorRef<GetTaggedArtifacts.Response> replyTo
        ) {
            this.limit = limit;
            this.offset = offset;
            this.replyTo = replyTo;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (RequestTaggedVersions) obj;
            return this.limit == that.limit &&
                this.offset == that.offset;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.limit, this.offset);
        }

        @Override
        public String toString() {
            return "RequestTaggedVersions[" +
                "limit=" + this.limit + ", " +
                "offset=" + this.offset + ']';
        }
    }

    final class RegisterTag implements TaggedCommand {
        public final String tagVersion;
        public final ActorRef<NotUsed> replyTo;

        public RegisterTag(
            final String tagVersion,
            final ActorRef<NotUsed> replyTo
        ) {
            this.tagVersion = tagVersion;
            this.replyTo = replyTo;
        }


        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final var that = (RegisterTag) obj;
            return Objects.equals(this.tagVersion, that.tagVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.tagVersion);
        }

        @Override
        public String toString() {
            return "RegisterTag[" +
                "tagVersion=" + this.tagVersion + ']';
        }
    }
}
