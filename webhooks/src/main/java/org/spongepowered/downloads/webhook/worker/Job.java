package org.spongepowered.downloads.webhook.worker;

public sealed interface Job {

    final record FetchJarMetadataJob(String mavenCoordinates, String componentId) implements Job {}
}
