package org.spongepowered.downloads.changelog.client.sonatype;

import io.vavr.collection.List;

public record Component(

    String id,
    String repository,
    String format,
    String group,
    String name,
    String version,
    List<Asset> assets
) {

    public static final record Asset(
        String downloadUrl,
        String path,
        String id,
        String repository,
        String format,
        Checksum checksum
    ) { }

    public static final record Checksum(
        String sha1,
        String md5
    ) { }
}
