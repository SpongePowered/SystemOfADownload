package org.spongepowered.downloads.git;

import java.util.UUID;

public record Repository(
    UUID entityId,
    String name,
    String url
) {

}
