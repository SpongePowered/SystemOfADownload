package org.spongepowered.downloads.webhook.sonatype;

import io.vavr.collection.List;

import java.util.Optional;

public final record ComponentSearchResponse(List<Item> items, Optional<String> continuationToken) {
    public final record Item(String id, String repository, String format, String group, String name, String version) {}
}
