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
final class SonatypeComponent {
    private final String id;
    private final String componentId;
    private final String format;
    private final String name;
    private final String group;
    private final String version;

    SonatypeComponent(
        final String id, final String componentId, final String format, final String name, final String group,
        final String version
    ) {
        this.id = id;
        this.componentId = componentId;
        this.format = format;
        this.name = name;
        this.group = group;
        this.version = version;
    }

    public String id() {
        return this.id;
    }

    public String componentId() {
        return this.componentId;
    }

    public String format() {
        return this.format;
    }

    public String name() {
        return this.name;
    }

    public String group() {
        return this.group;
    }

    public String version() {
        return this.version;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final var that = (SonatypeComponent) obj;
        return Objects.equals(this.id, that.id) &&
            Objects.equals(this.componentId, that.componentId) &&
            Objects.equals(this.format, that.format) &&
            Objects.equals(this.name, that.name) &&
            Objects.equals(this.group, that.group) &&
            Objects.equals(this.version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.componentId, this.format, this.name, this.group, this.version);
    }

    @Override
    public String toString() {
        return "SonatypeComponent[" +
            "id=" + this.id + ", " +
            "componentId=" + this.componentId + ", " +
            "format=" + this.format + ", " +
            "name=" + this.name + ", " +
            "group=" + this.group + ", " +
            "version=" + this.version + ']';
    }

}
