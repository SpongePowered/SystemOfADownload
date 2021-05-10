package org.spongepowered.downloads.maven.snapshot;

public class Snapshot {

    public final String timestamp;
    public final int buildNumber;

    public Snapshot(final String timestamp, final int buildNumber) {
        this.timestamp = timestamp;
        this.buildNumber = buildNumber;
    }
}
