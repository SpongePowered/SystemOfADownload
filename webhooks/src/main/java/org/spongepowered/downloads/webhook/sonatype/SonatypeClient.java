package org.spongepowered.downloads.webhook.sonatype;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.typesafe.config.ConfigException;
import io.vavr.Function0;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import io.vavr.jackson.datatype.VavrModule;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.git.api.CommitSha;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class SonatypeClient {

    private static final String ASSET_SEARCH = "service/rest/v1/search";
    private static final List<String> DEFAULT_SEARCH_ARGS = List.of("sort=version", "direction=asc", "maven.extension=jar", "maven.classifier");
    private static final Function0<SonatypeClient> SONATYPE_CLIENT = Function0.of(() -> {
        final SonatypeClient client = new SonatypeClient();

        try {
            client.config = new Config();
            return client;
        } catch (final ConfigException.Missing e) {
            throw new IllegalStateException("Malformed configuration file for sonatype-url", e);
        }
    }).memoized();

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private Config config;
    public static final class Config {
        // The base url like https://repo.sonatype.org/service/rest/v1/
        final String baseUrl = System.getenv().get("SONATYPE-URL");
        // The snapshot repo, like maven-snapshots
        final String snapshotRepo = System.getenv().get("SNAPSHOT-REPO");
        // The release repo, like maven-releases
        final String releaseRepo = System.getenv().get("RELEASE-REPO");
        // The public repo containing everything, like maven-public
        final String publicRepo = System.getenv("PUBLIC-REPO");

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

    private SonatypeClient() {
        this.mapper.registerModule(new VavrModule());
    }

    public static Function0<SonatypeClient> configureClient() {
        return SONATYPE_CLIENT;
    }

    private static Try<InputStream> openConnectionTo(final String target) {
        return Try.of(() -> URI.create(target))
            .mapTry(HttpRequest::newBuilder)
            .map(builder -> builder.timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .header("User-Agent", "SystemOfADownload-Crawler")
                .GET()
                .build()
            ).mapTry(httpRequest -> SonatypeClient.configureClient().get().client
                .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(HttpResponse::body)
                .join()
            );
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
        return SonatypeClient.openConnectionTo(target)
            .mapTry(reader -> this.mapper.readValue(reader, Component.class));
    }

    public Try<Component> resolveMavenArtifactWithComponentVersion(final String mavenGroup, final String mavenArtifactId, final String componentVersion) {
        final String baseUrl = this.config.baseUrl + "search?";
        final String target = new StringJoiner("&", baseUrl, "")
            .add("version=" + componentVersion)
            .add("maven.groupId=" + mavenGroup)
            .add("maven.artifactId=" + mavenArtifactId)
            .toString();
        return SonatypeClient.openConnectionTo(target)
            .mapTry(reader -> this.mapper.readValue(reader, Component.class));
    }

    public Try<Component> resolveArtifact(final String componentId) {
        final String target = this.config.baseUrl + "search?id=" + componentId;
        return SonatypeClient.openConnectionTo(target)
            .mapTry(reader -> this.mapper.readValue(reader, Component.class));
    }

    public Try<Option<String>> resolvePomVersion(final Component.Asset asset) {
        return SonatypeClient.openConnectionTo(asset.downloadUrl())
            .flatMapTry(reader -> {
                final Path pom = Files.createTempFile("system-of-a-download-files", "pom");
                return SonatypeClient.readFileFromInput(reader, pom);
            })
            .mapTry(pom -> Try.of(() -> new XmlMapper().readValue(pom.toFile(), MavenPom.class))
                .toOption()
                .map(MavenPom::version));
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

    private static CommitSha fromObjectId(final ObjectId oid) {
        // Seriously... why can't they just give us the damn 5 ints....
        final var bytes = new byte[Constants.OBJECT_ID_LENGTH];
        oid.copyTo(bytes, 0);

        final IntBuffer intBuf =
            ByteBuffer.wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .asIntBuffer();
        final int[] array = new int[intBuf.remaining()];
        intBuf.get(array);
        final long shaPart1 = (long) array[0] << 16 & array[1];
        final long shaPart2 = (long) array[2] << 16 & array[3];
        return new CommitSha(shaPart1, shaPart2, array[4]);
    }

    public Try<CommitSha> generateArtifactFrom(final Artifact asset) {
        return SonatypeClient.openConnectionTo(asset.downloadUrl())
            .flatMapTry(reader -> {
                final Path jar = Files.createTempFile("system-of-a-download-files", "jar");
                return readFileFromInput(reader, jar);
            })
            .flatMapTry(freshJarPath ->
                Try.withResources(() -> new JarInputStream(new FileInputStream(freshJarPath.toFile())))
                    .of(JarInputStream::getManifest)
                    .map(Manifest::getMainAttributes)
                    .map(attributes -> attributes.getValue("Git-Commit"))
                    .map(Optional::ofNullable)
                    .flatMap(opt -> opt.map(sha -> Try.of(() -> ObjectId.fromString(sha)))
                        .orElseGet(() -> Try.failure(new IllegalStateException(String.format("Could not process artifact %s", asset.getFormattedString(":"))))))
                    .map(SonatypeClient::fromObjectId)
            );

    }

    public Try<List<String>> getReleaseVersions(final String groupId, final String artifactId) {
        final String url = new StringJoiner("/").add(this.config.baseUrl)
            .add(this.config.releaseRepo)
            .add(groupId.replaceAll("\\.", "/"))
            .add(artifactId)
            .add(MavenConstants.MAVEN_METADATA_FILE)
            .toString();
        return SonatypeClient.openConnectionTo(url)
            .flatMapTry(reader -> {
                final Path pom = Files.createTempFile("system-of-a-download-files", "maven-metadata");
                return SonatypeClient.readFileFromInput(reader, pom);
            })
            .flatMapTry(pom -> Try.of(() -> new XmlMapper().readValue(pom.toFile(), MavenMetadata.class))
                .map(MavenMetadata::versioning)
                .map(MavenMetadata.Versioning::versions)
            );
    }

    public Try<Integer> getSnapshotBuildCount(final String groupId, final String artifactId, final String mavenVersion) {
        final String url = new StringJoiner("/").add(this.config.baseUrl)
            .add(this.config.snapshotRepo)
            .add(groupId.replaceAll("\\.", "/"))
            .add(artifactId)
            .add(mavenVersion)
            .add(MavenConstants.MAVEN_METADATA_FILE)
            .toString();
        return SonatypeClient.openConnectionTo(url)
            .flatMapTry(reader -> {
                final Path pom = Files.createTempFile("system-of-a-download-files", "maven-metadata");
                return SonatypeClient.readFileFromInput(reader, pom);
            })
            .flatMapTry(pom -> Try.of(() -> new XmlMapper().readValue(pom.toFile(), MavenMetadata.class))
                .toOption()
                .map(MavenMetadata::versioning)
                .map(MavenMetadata.Versioning::snapshot)
                .map(MavenMetadata.Snapshot::buildNumber)
                .toTry()
            );
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
        return SonatypeClient.openConnectionTo(completeUrl)
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
