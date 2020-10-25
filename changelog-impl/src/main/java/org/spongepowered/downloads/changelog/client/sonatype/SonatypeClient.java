package org.spongepowered.downloads.changelog.client.sonatype;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigException;
import io.vavr.Function0;
import io.vavr.control.Try;
import io.vavr.jackson.datatype.VavrModule;
import org.spongepowered.downloads.artifact.api.Artifact;
import play.api.Configuration;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class SonatypeClient {

    private String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    private SonatypeClient() throws MalformedURLException {
        this.mapper.registerModule(new VavrModule());
    }

    public static Function0<SonatypeClient> configureClient(final Configuration configuration) {
        return Function0.of(() -> {

            try {
                final SonatypeClient client = new SonatypeClient();
                client.baseUrl = configuration.underlying().getString("sonatype-url");
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

    public Try<Artifact> generateArtifactFrom(final Component.Asset asset) {
        return SonatypeClient.openConnectionTo(asset.downloadUrl())
            .mapTry(reader -> {
                final Path jar = Files.createTempFile("system-of-a-download-files", "jar");
                try (final BufferedInputStream in = new BufferedInputStream(reader);
                     final FileOutputStream out = new FileOutputStream(jar.toFile());) {
                    final byte[] dataBuffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                        out.write(dataBuffer, 0, bytesRead);
                    }
                }
                jar.

            })

    }
}
