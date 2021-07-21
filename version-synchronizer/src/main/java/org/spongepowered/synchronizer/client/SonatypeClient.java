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
package org.spongepowered.synchronizer.client;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.typesafe.config.ConfigException;
import io.vavr.Function0;
import io.vavr.Function1;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import io.vavr.jackson.datatype.VavrModule;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.downloads.maven.artifact.ArtifactMavenMetadata;
import org.spongepowered.downloads.sonatype.Component;
import org.spongepowered.downloads.sonatype.ComponentSearchResponse;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.StringJoiner;

public class SonatypeClient {

    private static final String ASSET_SEARCH = "service/rest/v1/search";
    private static final List<String> DEFAULT_SEARCH_ARGS = List.of("sort=version", "direction=asc", "maven.extension=jar", "maven.classifier");
    private static final Function1<ObjectMapper, SonatypeClient> SONATYPE_CLIENT = Function1.of((mapper) -> {
        final SonatypeClient client = new SonatypeClient(mapper);

        try {
            client.config = new Config();
            return client;
        } catch (final ConfigException.Missing e) {
            throw new IllegalStateException("Malformed configuration file for sonatype-url", e);
        }
    });

    private final ObjectMapper mapper;
    private Config config;

    public Try<ArtifactMavenMetadata> getArtifactMetadata(String groupId, String artifactId) {
        final var httpClient = HttpClient.newHttpClient();
        final var url = new StringJoiner("/", "https://repo.spongepowered.org/repository/maven-public/", "")
            .add(groupId)
            .add(artifactId)
            .add("maven-metadata.xml")
            .toString();
        final var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "SystemOfADownload-Synchronizer")
            .build();

        final var xmlmapper = new XmlMapper();
        xmlmapper.registerModule(new VavrModule());
        return Try.of(() -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()))
            .filter(response -> response.statusCode() == 200)
            .map(HttpResponse::body)
            .mapTry(is -> xmlmapper.readValue(is, ArtifactMavenMetadata.class));
    }

    public static final class Config {
        // The base url like https://repo.sonatype.org/service/rest/v1/
        final String baseUrl = System.getenv().get("SONATYPE-URL");
        // The snapshot repo, like maven-snapshots
        final String snapshotRepo = System.getenv().get("SNAPSHOT-REPO");
        // The release repo, like maven-releases
        final String releaseRepo = System.getenv().get("RELEASE-REPO");
        // The public repo containing everything, like maven-public
        final String publicRepo = System.getenv("PUBLIC-REPO");
        final String user = System.getenv("SONATYPE_USERNAME");
        final String password = System.getenv("SONATYPE_PASSWORD");

        Config() { }

        public String getBaseUrl() {
            return this.baseUrl;
        }

        public String getSnapshotRepo() {
            return this.snapshotRepo;
        }

        public String getReleaseRepo() {
            return this.releaseRepo;
        }

        public String getPublicRepo() {
            return this.publicRepo;
        }
    }

    public static Config getConfig() {
        return new Config();
    }

    private SonatypeClient(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static Function0<SonatypeClient> configureClient(
        final ObjectMapper objectMapper
    ) {
        return Function0.of(() -> SONATYPE_CLIENT.apply(objectMapper));
    }

    private Try<InputStream> openConnectionTo(final String target) {
        final Config config = this.config;
        final var userPass = config.user + ":" + config.password;
        final String encodedAuth = Base64.getEncoder().encodeToString(userPass.getBytes(
            StandardCharsets.UTF_8));
        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(target))
            .header("Content-Type", "application/json")
            .header("User-Agent", "Sponge-Downloader")
            .build();
        return Try.of(HttpClient::newHttpClient)
            .mapTry(client -> client.send(request, HttpResponse.BodyHandlers.ofInputStream()))
            .map(HttpResponse::body);
    }

    /**
     * Performs a REST api search with the following formula:
     * {@code https://$baseUrl/search?maven.groupId=$mavenGroup&maven.artifactId=$mavenArtifactId&maven.baseVersion=$mavenVersion}
     * Note that this is specific in that SNAPSHOT versions are ambiguous in
     * that if a snapshot version say comes with the timestamp associated, it
     * is not the valid maven version and instead should use
     * {@code ?version=$componentVersion}
     * @param mavenGroup
     * @param mavenArtifactId
     * @param mavenVersion
     * @return
     */
    public Try<Component> resolveMavenArtifact(final String mavenGroup, final String mavenArtifactId, final String mavenVersion) {
        final String baseUrl = this.config.baseUrl + "search?";
        final String target = new StringJoiner("&", baseUrl, "")
            .add("maven.groupId=" + mavenGroup)
            .add("maven.artifactId=" + mavenArtifactId)
            .add("maven.baseVersion=" + mavenVersion)
            .toString();
        return this.openConnectionTo(target)
            .mapTry(reader -> this.mapper.readValue(reader, Component.class));
    }

    public Try<Component> resolveMavenArtifactWithComponentVersion(final String mavenGroup, final String mavenArtifactId, final String componentVersion) {
        final String baseUrl = this.config.baseUrl + "search?";
        final String target = new StringJoiner("&", baseUrl, "")
            .add("version=" + componentVersion)
            .add("maven.groupId=" + mavenGroup)
            .add("maven.artifactId=" + mavenArtifactId)
            .toString();
        return this.openConnectionTo(target)
            .mapTry(reader -> this.mapper.readValue(reader, Component.class));
    }

    public Try<Component> resolveArtifact(final String componentId, final Logger logger) {
        final String target = this.config.baseUrl + "components/" + componentId;
        return this.openConnectionTo(target)
            .onFailure((throwable) -> logger.log(Level.ERROR, "Failed to retrieve component, maybe it doesn't exist? {}", componentId))
            .mapTry(reader -> {
                final JsonFactory factory = this.mapper.getFactory();
                final JsonParser parser = factory.createParser(reader.readAllBytes());
                final Component component = this.mapper.readValue(parser, Component.class);
                return component;
            })
            .onFailure(throwable -> {
                logger.log(Level.ERROR, "Failed to deserialize component for id {}", componentId);
                logger.throwing(throwable);
            })
            ;
    }


    private static Try<? extends Path> readFileFromInput(final InputStream reader, final Path pom) {
        return Try.withResources(
            () -> new BufferedInputStream(reader),
            () -> new FileOutputStream(pom.toFile())
        )
            .of((bis, file) -> {
                final var dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = bis.read(dataBuffer, 0, 1024)) != -1) {
                    file.write(dataBuffer, 0, bytesRead);
                }
                return pom;
            });
    }

    /*
    Basically we need to perform the following GET request:
    curl -X GET "
    https://repo-new.spongepowered.org/service/rest/v1/search/assets
    ?sort=version
    &direction=asc
    &repository=maven-snapshots
    &group=org.spongepowered
    &name=spongeapi
    &maven.baseVersion=8.0.0-SNAPSHOT
    &maven.extension=jar
    &maven.classifier
    " -H "accept: application/json"
    But, because sonatype has a hard stop at 50 assets, we need to use the continuation token
    up to the continueUpToBuildNumber if possible.
     */
    public Try<Map<Integer, String>> getSnapshotVersions(
        final String groupId,
        final String artifactID,
        final String mavenSnapshotVersion,
        final int continueUpToBuildNumber
    ) {
        final String url = new StringJoiner("/")
            .add(this.config.baseUrl)
            .add(ASSET_SEARCH)
            .toString();
        final StringJoiner argsBuilder = new StringJoiner("&");
        DEFAULT_SEARCH_ARGS.forEach(argsBuilder::add);
        argsBuilder.add("repository=" + this.config.snapshotRepo)
            .add("name=" + artifactID)
            .add("group=" + groupId)
            .add("maven.baseVersion=" + mavenSnapshotVersion);
        final String completeInitialRequestUrl = url + "?" + argsBuilder.toString();
        final Try<List<String>> componentSearchResponses = this.exhaustiveOnContinuationToken(completeInitialRequestUrl, null, continueUpToBuildNumber, List.empty());
        return componentSearchResponses.map(list -> list.zipWithIndex().toMap(Tuple2::_2, Tuple2::_1));
    }

    private Try<List<String>> exhaustiveOnContinuationToken(
        final String baseUrlWithArgs,
        @Nullable final String continuationToken,
        final int continueUntil,
        final List<String> existing
    ) {
        final String completeUrl = continuationToken == null ? baseUrlWithArgs : baseUrlWithArgs + "&continuationToken=" + continuationToken;
        if (continueUntil < existing.size()) {
            return Try.success(existing);
        }
        return this.openConnectionTo(completeUrl)
            .flatMapTry(reader -> {
                final var response = this.mapper.readValue(reader, ComponentSearchResponse.class);
                final List<String> versionsFromResponse = response.items().map(ComponentSearchResponse.Item::version);
                final List<String> found = existing.appendAll(versionsFromResponse);
                return response.continuationToken()
                    .map(token -> this.exhaustiveOnContinuationToken(
                        baseUrlWithArgs,
                        token,
                        continueUntil,
                        found
                    ))
                    .orElseGet(() -> Try.success(found));
            });
    }
}
