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
package org.spongepowered.downloads.artifact.api;

import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * In conjunction with {@link MavenCoordinates}, can be used to determine the
 * version type of the coordinates, and whether
 */
public enum VersionType {
    /**
     * A timestamp based file snapshot, such as {@code 1.0.0-20210118.163210-1}
     * to where it can be interpreted that the {@link #SNAPSHOT snapshot} version
     * would be {@code 1.0.0-SNAPSHOT} that happened to build at date time
     * {@code January 18th, 2021 at 16h32m10s} and it's the first build.
     */
    TIMESTAMP_SNAPSHOT {
        @Override
        public boolean isSnapshot() {
            return true;
        }

        @Override
        public String asStandardVersionString(final String version) {
            final var split = version.split("-");
            final var stringJoiner = new StringJoiner("-");
            for (int i = 0; i < split.length - 2; i++) {
                stringJoiner.add(split[i]);
            }

            return stringJoiner.add(SNAPSHOT_VERSION).toString();
        }
    },

    /**
     * A standard generic snapshot relative version of a release, such as {@code 1.0.0-SNAPSHOT}.
     */
    SNAPSHOT {
        @Override
        public boolean isSnapshot() {
            return true;
        }
    },

    /**
     * A standard release version not abiding by any snapshot guidelines, considered
     * final and singular, such as {@code 1.0.0}
     */
    RELEASE;

    /*
    Simple SNAPSHOT placeholder
     */
    private static final String SNAPSHOT_VERSION = "SNAPSHOT";

    /*
    Verifies the pattern that the snapshot version is date.time-build formatted,
    enables the pattern match for a timestamped snapshot
     */
    private static final Pattern VERSION_FILE_PATTERN = Pattern.compile("^(.*)-(\\d{8}.\\d{6})-(\\d+)$");

    private static final Pattern TIMESTAMP_TO_REPLACE = Pattern.compile("(\\d{8}.\\d{6})-(\\d+)$");

    public static VersionType fromVersion(final String version) {
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be empty");
        }
        // Simple check to find out if the version ends with SNAPSHOT.
        if (version.regionMatches(
            true,
            version.length() - SNAPSHOT_VERSION.length(),
            SNAPSHOT_VERSION,
            0,
            SNAPSHOT_VERSION.length()
        )) {
            return SNAPSHOT;
        }
        if (VERSION_FILE_PATTERN.matcher(version).matches()) {
            return TIMESTAMP_SNAPSHOT;
        }
        return RELEASE;
    }

    public boolean isSnapshot() {
        return false;
    }

    public String asStandardVersionString(final String version) {
        return version;
    }
}
