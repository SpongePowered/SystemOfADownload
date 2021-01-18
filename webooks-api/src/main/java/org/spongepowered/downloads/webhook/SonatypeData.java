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
package org.spongepowered.downloads.webhook;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;

@JsonDeserialize
final class SonatypeData {
    private final String timestamp;
    private final String nodeId;
    private final String initiator;
    private final String repositoryName;
    private final String action;
    private final SonatypeComponent component;

    SonatypeData(
        final String timestamp, final String nodeId, final String initiator, final String repositoryName, final String action,
        final SonatypeComponent component
    ) {
        this.timestamp = timestamp;
        this.nodeId = nodeId;
        this.initiator = initiator;
        this.repositoryName = repositoryName;
        this.action = action;
        this.component = component;
    }

    public String timestamp() {
        return this.timestamp;
    }

    public String nodeId() {
        return this.nodeId;
    }

    public String initiator() {
        return this.initiator;
    }

    public String repositoryName() {
        return this.repositoryName;
    }

    public String action() {
        return this.action;
    }

    public SonatypeComponent component() {
        return this.component;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final var that = (SonatypeData) obj;
        return Objects.equals(this.timestamp, that.timestamp) &&
            Objects.equals(this.nodeId, that.nodeId) &&
            Objects.equals(this.initiator, that.initiator) &&
            Objects.equals(this.repositoryName, that.repositoryName) &&
            Objects.equals(this.action, that.action) &&
            Objects.equals(this.component, that.component);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            this.timestamp, this.nodeId, this.initiator, this.repositoryName, this.action, this.component);
    }

    @Override
    public String toString() {
        return "SonatypeData[" +
            "timestamp=" + this.timestamp + ", " +
            "nodeId=" + this.nodeId + ", " +
            "initiator=" + this.initiator + ", " +
            "repositoryName=" + this.repositoryName + ", " +
            "action=" + this.action + ", " +
            "component=" + this.component + ']';
    }

}
