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
package org.spongepowered.downloads.maven;


import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.vavr.collection.List;
import io.vavr.jackson.datatype.VavrModule;
import org.junit.Test;
import org.spongepowered.downloads.maven.artifact.ArtifactMavenMetadata;
import org.spongepowered.downloads.maven.artifact.Versioning;

import java.io.IOException;

public final class MavenMetadataTest {

    @Test
    public void TestMavenMetadataDeserialization() throws IOException {
        final var mapper = new XmlMapper();
        mapper.registerModule(new VavrModule());
        final var mavenMetadataFile = getClass().getClassLoader().getResourceAsStream("maven-metadata-example.xml");
        final var artifactMavenMetadata = mapper.readValue(mavenMetadataFile, ArtifactMavenMetadata.class);
        // assertions
        assert "spongeapi".equals(artifactMavenMetadata.artifactId());
        assert "org.spongepowered".equals(artifactMavenMetadata.groupId());
        final var existingVersions = List.of(
            "1.0.0-SNAPSHOT",
            "1.0",
            "1.1-SNAPSHOT",
            "2.0",
            "2.1-SNAPSHOT",
            "3.0.0",
            "3.0.1-SNAPSHOT",
            "3.0.1-indev",
            "3.1.0-SNAPSHOT",
            "3.1.0",
            "4.0.0-SNAPSHOT",
            "4.0.0",
            "4.0.1",
            "4.0.2",
            "4.0.3",
            "4.1.0-SNAPSHOT",
            "4.1.0",
            "4.2.0-SNAPSHOT",
            "5.0.0-SNAPSHOT",
            "5.0.0",
            "5.1.0-SNAPSHOT",
            "5.1.0",
            "5.2.0-SNAPSHOT",
            "6.0.0-SNAPSHOT",
            "6.0.0",
            "6.1.0-SNAPSHOT",
            "7.0.0-SNAPSHOT",
            "7.0.0",
            "7.1.0-SNAPSHOT",
            "7.1.0",
            "7.2.0-SNAPSHOT",
            "7.2.0",
            "7.3.0-SNAPSHOT",
            "7.3.0",
            "7.4.0-SNAPSHOT",
            "8.0.0-SNAPSHOT",
            "9.0.0-SNAPSHOT"
        );
        final var expected = new Versioning(
            "9.0.0-SNAPSHOT",
            "7.3.0",
            "20210616221657",
            existingVersions
        );
        assert expected.equals(artifactMavenMetadata.versioning());
    }
}
