package org.spongepowered.downloads.maven;

import java.util.UUID;

public record Repository(
    UUID repoId,
    String url,
    String name,

) {
}
