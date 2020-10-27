package org.spongepowered.downloads.webhook.sonatype;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigException;
import io.vavr.Function0;
import io.vavr.control.Try;
import io.vavr.jackson.datatype.VavrModule;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.spongepowered.downloads.artifact.api.Artifact;
import org.spongepowered.downloads.git.api.CommitSha;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class SonatypeClient {

    private String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    private SonatypeClient() throws MalformedURLException {
        this.mapper.registerModule(new VavrModule());
    }

    public static Function0<SonatypeClient> configureClient() {
        return Function0.of(() -> {

            try {
                final SonatypeClient client = new SonatypeClient();
                client.baseUrl = System.getenv().get("SONATYPE-URL");
                return client;
            } catch (final ConfigException.Missing e) {
                throw new IllegalStateException("Malformed configuration file for sonatype-url", e);
            } catch (final MalformedURLException e) {
                throw new IllegalStateException("Malformed url for the maven repo");
            }
        }).memoized();
    }

    private static Try<InputStream> openConnectionTo(final String target) {
        return Try.of(() -> new URL(target))
            .mapTry(URL::openConnection)
            .mapTry(url -> (HttpURLConnection) url)
            .mapTry(connection -> {
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "Sponge-Downloader");
                connection.connect();
                return connection.getInputStream();
            });
    }

    public Try<Component> resolveArtifact(final String componentId) {
        final String target = this.baseUrl + componentId;
        return SonatypeClient.openConnectionTo(target)
            .mapTry(reader -> this.mapper.readValue(reader, Component.class));
    }

    private static CommitSha fromObjectId(ObjectId oid) {
        // Seriously... why can't they just give us the damn 5 ints....
        final var bytes = new byte[Constants.OBJECT_ID_LENGTH];
        oid.copyTo(bytes, 0);

        final IntBuffer intBuf =
            ByteBuffer.wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .asIntBuffer();
        final int[] array = new int[intBuf.remaining()];
        intBuf.get(array);
        final long shaPart1 = array[0] << 16 & array[1];
        final long shaPart2 = array[2] << 16 & array[3];
        return new CommitSha(shaPart1, shaPart2, array[4]);
    }

    public Try<CommitSha> generateArtifactFrom(final Artifact asset) {
        return SonatypeClient.openConnectionTo(asset.downloadUrl())
            .flatMapTry(reader -> {
                final Path jar = Files.createTempFile("system-of-a-download-files", "jar");
                return Try.withResources(
                    () -> new BufferedInputStream(reader),
                    () -> new FileOutputStream(jar.toFile())
                )
                    .of((bis, fos) -> {
                        // Download and write 1Kb at a time.
                        final var dataBuffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = bis.read(dataBuffer, 0, 1024)) != -1) {
                            fos.write(dataBuffer, 0, bytesRead);
                        }
                        return jar;
                    });
            })
            .flatMapTry(freshJarPath ->
                Try.withResources(() -> new JarInputStream(new FileInputStream(freshJarPath.toFile())))
                    .of(JarInputStream::getManifest)
                    .map(Manifest::getMainAttributes)
                    .map(attributes -> attributes.getValue("Git-Commit"))
                    .mapTry(ObjectId::fromString)
                    .map(SonatypeClient::fromObjectId));

    }
}
