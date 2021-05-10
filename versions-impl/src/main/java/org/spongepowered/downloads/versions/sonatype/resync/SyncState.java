package org.spongepowered.downloads.versions.sonatype.resync;

import org.spongepowered.downloads.maven.artifact.ArtifactMavenMetadata;
import org.spongepowered.downloads.maven.artifact.Versioning;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

final class SyncState {

    public final String groupId;
    public final String artifactId;
    public final String lastUpdated;
    public final ArtifactMavenMetadata versions;

    public static DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR, 4)
        .appendValue(ChronoField.MONTH_OF_YEAR, 2)
        .appendValue(ChronoField.DAY_OF_MONTH, 2)
        .appendValue(ChronoField.HOUR_OF_DAY, 2)
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .toFormatter();
    static final SyncState EMPTY = new SyncState("", new ArtifactMavenMetadata("", "", new Versioning()));

    public SyncState(
        final String lastUpdated,
        final ArtifactMavenMetadata versions
    ) {
        this.lastUpdated = lastUpdated;
        this.versions = versions;
        this.groupId = versions.groupId();
        this.artifactId = versions.artifactId();
    }
}
